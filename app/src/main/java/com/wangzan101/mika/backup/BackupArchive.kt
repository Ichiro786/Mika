package com.wangzan101.mika.backup

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 备份文件读写失败的原因;界面据此给出不同的提示。 */
class BackupException(val kind: Kind, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Kind { NOT_A_BACKUP, UNSUPPORTED_VERSION, WRONG_PASSWORD, CORRUPT, UNSAFE, TOO_LARGE }
}

/**
 * 备份文件:头部(魔数、版本、PBKDF2 迭代次数、盐、IV)后面是分块的 AES-256-GCM 密文。
 * 每块 `[标志 1 字节][密文长度 4 字节][密文+标签]`,标志 1 表示最后一块;块的 nonce 是头里的 IV 异或块序号,
 * 头、标志和块序号都进 AAD,所以截断、重排、改头都会验证失败。明文是一个 zip,可以边读边写,不必整个装进内存。
 * 纯 JVM(不依赖 Android),单元测试直接跑。
 */
object BackupCrypto {
    private val MAGIC = "ORBITBAK".toByteArray(Charsets.US_ASCII)
    const val VERSION = 1
    const val DEFAULT_ITERATIONS = 310_000
    private const val MIN_ITERATIONS = 1_000
    private const val MAX_ITERATIONS = 10_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BYTES = 16
    private const val CHUNK = 64 * 1024
    private const val HEADER_BYTES = 8 + 1 + 4 + SALT_BYTES + IV_BYTES

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun cipher(mode: Int, key: SecretKeySpec, header: ByteArray, baseIv: ByteArray, index: Long, last: Boolean): Cipher {
        val nonce = baseIv.copyOf()
        for (i in 0 until 8) {
            val b = ((index ushr (56 - 8 * i)) and 0xFF).toInt()
            nonce[IV_BYTES - 8 + i] = (nonce[IV_BYTES - 8 + i].toInt() xor b).toByte()
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(mode, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
        c.updateAAD(header)
        c.updateAAD(byteArrayOf(if (last) 1 else 0))
        val idx = ByteArray(8) { i -> ((index ushr (56 - 8 * i)) and 0xFF).toByte() }
        c.updateAAD(idx)
        return c
    }

    /** 返回明文输出流;写完必须 close(会写最后一块并关掉 [out])。 */
    fun encrypt(
        out: OutputStream,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): OutputStream {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "iterations" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteArrayOutputStream(HEADER_BYTES).apply {
            write(MAGIC); write(VERSION); writeInt(this, iterations); write(salt); write(iv)
        }.toByteArray()
        val key = deriveKey(password, salt, iterations)
        out.write(header)
        return EncryptingOutput(out, key, header, iv)
    }

    /** 读头并派生密钥,返回验证过的明文输入流;密码不对在第一次读时抛 [BackupException.Kind.WRONG_PASSWORD]。 */
    fun decrypt(input: InputStream, password: CharArray): InputStream {
        val header = ByteArray(HEADER_BYTES)
        try {
            readFully(input, header)
        } catch (e: EOFException) {
            throw BackupException(BackupException.Kind.NOT_A_BACKUP, "Not an Orbit backup", e)
        }
        if (!header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BackupException(BackupException.Kind.NOT_A_BACKUP, "Not an Orbit backup")
        }
        if (header[8].toInt() != VERSION) {
            throw BackupException(BackupException.Kind.UNSUPPORTED_VERSION, "Unsupported backup version ${header[8]}")
        }
        val iterations = ((header[9].toInt() and 0xFF) shl 24) or ((header[10].toInt() and 0xFF) shl 16) or
            ((header[11].toInt() and 0xFF) shl 8) or (header[12].toInt() and 0xFF)
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw BackupException(BackupException.Kind.CORRUPT, "Bad iteration count")
        }
        val salt = header.copyOfRange(13, 13 + SALT_BYTES)
        val iv = header.copyOfRange(13 + SALT_BYTES, HEADER_BYTES)
        return DecryptingInput(input, deriveKey(password, salt, iterations), header, iv)
    }

    private fun writeInt(out: OutputStream, v: Int) {
        out.write(v ushr 24); out.write(v ushr 16); out.write(v ushr 8); out.write(v)
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) throw EOFException()
            n += r
        }
    }

    private class EncryptingOutput(
        private val out: OutputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val iv: ByteArray,
    ) : OutputStream() {
        private val buf = ByteArray(CHUNK)
        private var n = 0
        private var index = 0L
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "closed" }
            var o = off
            var left = len
            while (left > 0) {
                // 满了的一块要等到确认后面还有数据才发出去,这样最后一块才能带上「最后」标志
                if (n == CHUNK) emit(false)
                val c = minOf(left, CHUNK - n)
                System.arraycopy(b, o, buf, n, c)
                n += c; o += c; left -= c
            }
        }

        private fun emit(last: Boolean) {
            val ct = try {
                cipher(Cipher.ENCRYPT_MODE, key, header, iv, index, last).doFinal(buf, 0, n)
            } catch (e: GeneralSecurityException) {
                throw IOException("Encryption failed", e)
            }
            out.write(if (last) 1 else 0)
            writeInt(out, ct.size)
            out.write(ct)
            index++
            n = 0
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            try {
                emit(true)
                out.flush()
            } finally {
                out.close()
            }
        }
    }

    private class DecryptingInput(
        private val input: InputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val iv: ByteArray,
    ) : InputStream() {
        private var cur = ByteArray(0)
        private var pos = 0
        private var done = false
        private var index = 0L

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= cur.size) {
                if (done) return -1
                next()
            }
            val c = minOf(len, cur.size - pos)
            System.arraycopy(cur, pos, b, off, c)
            pos += c
            return c
        }

        private fun next() {
            val first = index == 0L
            val flag = input.read()
            if (flag < 0) throw BackupException(BackupException.Kind.CORRUPT, "Backup is truncated")
            if (flag != 0 && flag != 1) throw BackupException(BackupException.Kind.CORRUPT, "Bad block flag")
            val lenBytes = ByteArray(4)
            val len = try {
                readFully(input, lenBytes)
                ((lenBytes[0].toInt() and 0xFF) shl 24) or ((lenBytes[1].toInt() and 0xFF) shl 16) or
                    ((lenBytes[2].toInt() and 0xFF) shl 8) or (lenBytes[3].toInt() and 0xFF)
            } catch (e: EOFException) {
                throw BackupException(BackupException.Kind.CORRUPT, "Backup is truncated", e)
            }
            if (len < TAG_BYTES || len > CHUNK + TAG_BYTES) throw BackupException(BackupException.Kind.CORRUPT, "Bad block size")
            val ct = ByteArray(len)
            try {
                readFully(input, ct)
            } catch (e: EOFException) {
                throw BackupException(BackupException.Kind.CORRUPT, "Backup is truncated", e)
            }
            cur = try {
                cipher(Cipher.DECRYPT_MODE, key, header, iv, index, flag == 1).doFinal(ct)
            } catch (e: AEADBadTagException) {
                if (first) throw BackupException(BackupException.Kind.WRONG_PASSWORD, "Wrong password or damaged backup", e)
                throw BackupException(BackupException.Kind.CORRUPT, "Backup is damaged", e)
            } catch (e: GeneralSecurityException) {
                throw BackupException(BackupException.Kind.CORRUPT, "Backup is damaged", e)
            }
            pos = 0
            index++
            if (flag == 1) {
                done = true
                if (input.read() != -1) throw BackupException(BackupException.Kind.CORRUPT, "Unexpected data after the end of the backup")
            }
        }

        override fun close() = input.close()
    }
}

/** 备份里放什么、怎么打成 zip、怎么安全地拆出来、怎么合并回应用目录。纯 JVM。 */
object BackupArchive {
    const val CONVERSATIONS = "conversations"
    const val SKILLS = "skills/personal"
    private val SEGMENT = Regex("[A-Za-z0-9._-]+")
    private const val MAX_ENTRIES = 200_000
    private const val MAX_TOTAL_BYTES = 16L shl 30

    /** 要备份的文件:对话目录(跳过 `current`、临时文件)和个人技能目录里的普通文件;不跟符号链接。 */
    fun collect(filesDir: File): List<Pair<String, File>> {
        val out = mutableListOf<Pair<String, File>>()
        fun walk(rel: String, skipTransient: Boolean) {
            val root = File(filesDir, rel)
            if (!root.isDirectory) return
            root.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }.forEach { f ->
                if (!f.isFile || Files.isSymbolicLink(f.toPath())) return@forEach
                if (skipTransient && (f.name == "current" || f.name.endsWith(".tmp"))) return@forEach
                val name = f.relativeTo(root).invariantSeparatorsPath
                if (name.split('/').all { SEGMENT.matches(it) && it != "." && it != ".." }) out += "$rel/$name" to f
            }
        }
        walk(CONVERSATIONS, true)
        walk(SKILLS, false)
        return out
    }

    fun writeZip(out: OutputStream, entries: List<Pair<String, File>>) {
        val zip = ZipOutputStream(out)
        // 空备份也要有一个条目(部分 JDK 的 ZipOutputStream 不允许零条目);拆包时这个条目在范围外,被忽略
        zip.putNextEntry(ZipEntry("orbit-backup.txt"))
        zip.write("Orbit backup v${BackupCrypto.VERSION}\n".toByteArray(Charsets.US_ASCII))
        zip.closeEntry()
        for ((name, file) in entries) {
            val input = try { file.inputStream() } catch (_: FileNotFoundException) { continue }
            input.use {
                zip.putNextEntry(ZipEntry(name))
                it.copyTo(zip)
                zip.closeEntry()
            }
        }
        zip.finish()
    }

    /** 条目名是否安全且属于备份范围;不安全(绝对路径、`..`、反斜杠、奇怪字符)抛异常,范围之外返回 false。 */
    internal fun accept(name: String): Boolean {
        val unsafe = name.isEmpty() || name.startsWith("/") || name.contains('\\') || name.contains('\u0000')
        val segments = name.split('/')
        if (unsafe || segments.any { it == ".." || it == "." || !SEGMENT.matches(it) }) {
            throw BackupException(BackupException.Kind.UNSAFE, "Unsafe path in backup")
        }
        return name.startsWith("$CONVERSATIONS/") || name.startsWith("$SKILLS/")
    }

    /**
     * 把 [input](已解密的 zip)拆到空目录 [dest];读完整个流,最后一块的认证也就验过了。返回拆出的文件数。
     * 条目路径不安全就整个拒绝;备份范围之外的条目忽略。
     */
    fun extract(input: InputStream, dest: File, maxTotalBytes: Long = MAX_TOTAL_BYTES): Int {
        val root = dest.canonicalFile
        var files = 0
        var total = 0L
        var entries = 0
        val zip = ZipInputStream(input)
        val buf = ByteArray(32 * 1024)
        while (true) {
            val e: ZipEntry? = try {
                zip.nextEntry
            } catch (ex: java.util.zip.ZipException) {
                throw BackupException(BackupException.Kind.CORRUPT, "Backup is damaged", ex)
            }
            if (e == null) break
            if (++entries > MAX_ENTRIES) throw BackupException(BackupException.Kind.TOO_LARGE, "Too many entries")
            val name = if (e.isDirectory) e.name.removeSuffix("/") else e.name
            if (!accept(name) || e.isDirectory) continue
            val target = File(root, name).canonicalFile
            if (!target.path.startsWith(root.path + File.separator)) {
                throw BackupException(BackupException.Kind.UNSAFE, "Unsafe path in backup")
            }
            target.parentFile?.mkdirs()
            target.outputStream().use { o ->
                while (true) {
                    val n = zip.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxTotalBytes) throw BackupException(BackupException.Kind.TOO_LARGE, "Backup is too large")
                    o.write(buf, 0, n)
                }
            }
            files++
        }
        // ZipInputStream 读到中央目录就停;把剩下的读完,GCM 最后一块的验证才会发生
        while (input.read(buf) >= 0) { /* drain */ }
        return files
    }

    class MergeResult(val addedFiles: Int, val skippedFiles: Int, val conversationIds: Set<String>)

    /**
     * 把 [staged](extract 的结果)合并进 [filesDir]:本地没有的文件复制过去;对话(`<id>.json`)或技能目录
     * 已经在本地,就整个保留本地的,备份里同名的不动。从不覆盖、从不删除。
     */
    fun merge(staged: File, filesDir: File): MergeResult {
        var added = 0
        var skipped = 0
        val ids = linkedSetOf<String>()
        fun mergeTree(rel: String, group: (String) -> String, exists: (File, String) -> Boolean, onAdded: (String) -> Unit) {
            val src = File(staged, rel)
            if (!src.isDirectory) return
            val dst = File(filesDir, rel)
            val byGroup = src.walkTopDown().filter { it.isFile }.groupBy { group(it.relativeTo(src).invariantSeparatorsPath) }
            for ((g, list) in byGroup) {
                if (exists(dst, g)) { skipped += list.size; continue }
                for (f in list) {
                    val target = File(dst, f.relativeTo(src).path)
                    if (target.exists()) { skipped++; continue }
                    target.parentFile?.mkdirs()
                    val tmp = File(target.parentFile, "${target.name}.import-tmp")
                    Files.copy(f.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    try {
                        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    } catch (_: IOException) {
                        // 目标在复制期间出现了:不覆盖
                        tmp.delete(); skipped++; continue
                    }
                    added++
                    onAdded(g)
                }
            }
        }
        mergeTree(CONVERSATIONS, { it.substringBefore('/').substringBefore('.') }, { d, id -> File(d, "$id.json").exists() }) { ids += it }
        mergeTree(SKILLS, { it.substringBefore('/') }, { d, name -> File(d, name).exists() }) { }
        return MergeResult(added, skipped, ids)
    }
}
