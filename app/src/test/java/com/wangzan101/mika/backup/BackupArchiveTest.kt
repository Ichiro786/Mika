package com.wangzan101.mika.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule val tmp = TemporaryFolder()
    private val pw = "correct horse".toCharArray()
    private val fast = 1_000

    private fun encrypt(plain: ByteArray, password: CharArray = pw): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(out, password, fast).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun decrypt(data: ByteArray, password: CharArray = pw): ByteArray =
        BackupCrypto.decrypt(ByteArrayInputStream(data), password).use { it.readBytes() }

    private fun kind(block: () -> Unit): BackupException.Kind {
        try { block() } catch (e: BackupException) { return e.kind }
        fail("expected BackupException"); throw AssertionError()
    }

    @Test fun cryptoRoundTripSmallEmptyAndMultiChunk() {
        for (size in listOf(0, 1, 65_535, 65_536, 65_537, 200_000)) {
            val plain = ByteArray(size) { (it * 31).toByte() }
            assertArrayEquals("size $size", plain, decrypt(encrypt(plain)))
        }
    }

    @Test fun wrongPasswordIsReported() {
        val data = encrypt("hello".toByteArray())
        assertEquals(BackupException.Kind.WRONG_PASSWORD, kind { decrypt(data, "nope nope".toCharArray()) })
    }

    @Test fun tamperingAndTruncationAreDetected() {
        val plain = ByteArray(150_000) { it.toByte() }
        val data = encrypt(plain)
        val flipped = data.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 1).toByte() }
        assertEquals(BackupException.Kind.CORRUPT, kind { decrypt(flipped) })
        // cut at a block boundary of the first chunk: the final chunk is missing
        val firstChunkEnd = 41 + 1 + 4 + 65_536 + 16
        assertEquals(BackupException.Kind.CORRUPT, kind { decrypt(data.copyOf(firstChunkEnd)) })
        assertEquals(BackupException.Kind.CORRUPT, kind { decrypt(data + byteArrayOf(0)) })
    }

    @Test fun notABackupAndFutureVersion() {
        assertEquals(BackupException.Kind.NOT_A_BACKUP, kind { decrypt("PK\u0003\u0004 not ours, long enough to read a header....".toByteArray()) })
        assertEquals(BackupException.Kind.NOT_A_BACKUP, kind { decrypt(ByteArray(3)) })
        val data = encrypt(ByteArray(10)).also { it[8] = 9 }
        assertEquals(BackupException.Kind.UNSUPPORTED_VERSION, kind { decrypt(data) })
    }

    @Test fun ciphertextDiffersEachTime() {
        val plain = "same".toByteArray()
        assertFalse(encrypt(plain).contentEquals(encrypt(plain)))
    }

    private fun filesDir(): File = tmp.newFolder("files").also { root ->
        File(root, "conversations").apply {
            mkdirs()
            File(this, "aaa.json").writeText("[A]")
            File(this, "aaa.title").writeText("title A")
            File(this, "bbb.json").writeText("[B]")
            File(this, "current").writeText("aaa")
            File(this, "ccc.json.tmp").writeText("partial")
        }
        File(root, "skills/personal/my-skill").apply {
            mkdirs()
            File(this, "SKILL.md").writeText("skill")
        }
        File(root, "skills/pending/x").mkdirs()
    }

    @Test fun collectSkipsTransientAndIncludesSkills() {
        val names = BackupArchive.collect(filesDir()).map { it.first }.toSet()
        assertEquals(setOf("conversations/aaa.json", "conversations/aaa.title", "conversations/bbb.json",
            "skills/personal/my-skill/SKILL.md"), names)
    }

    private fun backupOf(dir: File): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(out, pw, fast).use { BackupArchive.writeZip(it, BackupArchive.collect(dir)) }
        return out.toByteArray()
    }

    @Test fun fullRoundTripAndMergeKeepsExisting() {
        val data = backupOf(filesDir())
        // target phone already has a different "aaa" conversation and nothing else
        val target = tmp.newFolder("target")
        File(target, "conversations").mkdirs()
        File(target, "conversations/aaa.json").writeText("[LOCAL]")
        val stage = tmp.newFolder("stage")
        val n = BackupCrypto.decrypt(ByteArrayInputStream(data), pw).use { BackupArchive.extract(it, stage) }
        assertEquals(4, n)
        val r = BackupArchive.merge(stage, target)
        assertEquals("[LOCAL]", File(target, "conversations/aaa.json").readText())
        assertFalse(File(target, "conversations/aaa.title").exists())
        assertEquals("[B]", File(target, "conversations/bbb.json").readText())
        assertEquals("skill", File(target, "skills/personal/my-skill/SKILL.md").readText())
        assertEquals(setOf("bbb"), r.conversationIds)
        assertEquals(2, r.addedFiles)
        assertEquals(2, r.skippedFiles)
        // merging again changes nothing
        val again = BackupArchive.merge(stage, target)
        assertEquals(0, again.addedFiles)
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> for ((n, c) in entries) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
        return out.toByteArray()
    }

    @Test fun zipSlipIsRejected() {
        for (bad in listOf("../evil.json", "conversations/../../evil", "/abs/path", "conversations/a\\b.json", "conversations//x.json")) {
            val dest = tmp.newFolder()
            val k = kind { BackupArchive.extract(ByteArrayInputStream(zipOf(bad to "x")), dest) }
            assertEquals(bad, BackupException.Kind.UNSAFE, k)
            assertEquals(0, dest.listFiles()!!.size)
        }
    }

    @Test fun entriesOutsideBackupScopeAreIgnored() {
        val dest = tmp.newFolder()
        val n = BackupArchive.extract(ByteArrayInputStream(zipOf("random.txt" to "x", "conversations/ok.json" to "[]")), dest)
        assertEquals(1, n)
        assertTrue(File(dest, "conversations/ok.json").isFile)
        assertFalse(File(dest, "random.txt").exists())
    }

    @Test fun sizeCapIsEnforced() {
        val dest = tmp.newFolder()
        val k = kind { BackupArchive.extract(ByteArrayInputStream(zipOf("conversations/a.json" to "x".repeat(100))), dest, maxTotalBytes = 10) }
        assertEquals(BackupException.Kind.TOO_LARGE, k)
    }
}
