package com.wangzan101.mika.gemini

import android.content.Context
import android.os.StatFs
import android.system.Os
import com.wangzan101.mika.cap.sandbox.ProcRunner
import com.wangzan101.mika.cap.sandbox.ProotDevices
import com.wangzan101.mika.core.cap.sandbox.RootfsExtractor
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The small Linux that runs Google's Antigravity ACP server on the phone, and its on-phone setup.
 *
 * Everything is downloaded on the phone, never bundled: Ubuntu's base system (glibc) from
 * cdimage.ubuntu.com, CPython from python-build-standalone, and Google's server from dl.google.com
 * (the URL and sha256 come from the official ACP registry). Kept lean: docs, man pages, locales, apt
 * caches, CPython's tests and Tk are removed, Google's download is deleted once unpacked, and only
 * one server version is kept. Runs with Orbit's own PRoot, apart from Orbit's script sandbox.
 */
class GeminiRuntime(private val context: Context) {
    val root = File(context.filesDir, "gm")
    val rootfs = File(root, "r")
    private val partial = File(root, "r.partial")
    private val downloads = File(root, "dl")
    private val tmp = File(context.cacheDir, "gm-tmp")
    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)

    val ready: Boolean get() = File(rootfs, "root/agy/current/.ready").exists()
    val installedVersion: String? get() = runCatching { File(rootfs, "root/agy/current/.ready").readText().trim() }.getOrNull()
    val supported: Boolean get() = File(nativeDir, "libproot.so").exists() && android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a")

    /** Host path of a guest path. */
    fun host(guest: String) = File(rootfs, guest.trimStart('/'))

    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

    fun interface Progress { fun step(stage: Stage, percent: Int) }
    enum class Stage { Space, Base, Python, Server, Prepare, Done }

    class SetupError(message: String) : IOException(message)

    suspend fun setup(progress: Progress) = withContext(Dispatchers.IO) {
        if (!supported) throw SetupError("This phone can't run the Gemini engine (needs a 64-bit ARM phone).")
        root.mkdirs(); downloads.mkdirs(); tmp.mkdirs()
        progress.step(Stage.Space, 0)
        val free = StatFs(root.path).availableBytes
        if (free < NEEDED_FREE) throw SetupError("Not enough storage: ${free / MB} MB free, about ${NEEDED_FREE / MB} MB needed while setting up.")
        if (!File(rootfs, ".base-ok").exists()) installBase(progress)
        if (!File(rootfs, "opt/python/bin/python3").exists()) installPython(progress)
        installTools()
        installServer(progress)
        RootfsExtractor.deleteTree(downloads)
        progress.step(Stage.Done, 100)
    }

    fun remove() {
        RootfsExtractor.deleteTree(root)
        RootfsExtractor.deleteTree(tmp)
    }

    /** Space Orbit's Gemini engine takes on the phone, in bytes (links not followed). */
    fun usedBytes(): Long {
        if (!root.exists()) return 0
        var total = 0L
        java.nio.file.Files.walk(root.toPath()).use { s ->
            s.forEach { p -> if (java.nio.file.Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) total += runCatching { java.nio.file.Files.size(p) }.getOrDefault(0) }
        }
        return total
    }

    // ---- base system ----

    private suspend fun installBase(progress: Progress) {
        progress.step(Stage.Base, 0)
        val dir = "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/"
        val sums = text(dir + "SHA256SUMS")
        val (sha, name) = sums.lines().mapNotNull { l ->
            BASE.find(l.trim())?.let { it.groupValues[1] to it.groupValues[2] }
        }.maxByOrNull { (_, n) -> n.substringAfter("24.04.").substringBefore('-').toIntOrNull() ?: 0 }
            ?: throw SetupError("Couldn't find Ubuntu's base system for this phone.")
        val file = File(downloads, name)
        download(dir + name, file, sha) { progress.step(Stage.Base, it * 8 / 10) }
        RootfsExtractor.deleteTree(partial)
        file.inputStream().use { RootfsExtractor.extract(it, partial) }
        file.delete()
        slimBase(partial)
        RootfsExtractor.deleteTree(rootfs)
        if (!partial.renameTo(rootfs)) throw SetupError("Couldn't finish unpacking the base system.")
        File(rootfs, ".base-ok").writeText(name + "\n")
        progress.step(Stage.Base, 100)
    }

    private fun slimBase(r: File) {
        for (p in listOf("usr/share/doc", "usr/share/man", "usr/share/info", "usr/share/locale", "usr/share/lintian",
            "var/cache/apt", "var/lib/apt/lists", "var/cache/debconf", "var/log")) RootfsExtractor.deleteTree(File(r, p))
        File(r, "etc/resolv.conf").let { it.delete(); it.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n") }
        File(r, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost ip6-localhost\n")
        for (d in listOf("tmp", "root", "run/orbit", "dev", "proc", "sys")) File(r, d).mkdirs()
        val dev = File(r, "dev")
        for ((name, target) in ProotDevices.DEV_SYMLINKS) {
            val link = File(dev, name)
            if (!link.exists()) runCatching { Os.symlink(target, link.path) }
        }
    }

    // ---- CPython ----

    private suspend fun installPython(progress: Progress) {
        progress.step(Stage.Python, 0)
        val release = Json.parseToJsonElement(text("https://api.github.com/repos/astral-sh/python-build-standalone/releases/tags/$PBS_RELEASE")).jsonObject
        val asset = (release["assets"] as? JsonArray)?.map { it.jsonObject }
            ?.firstOrNull { PBS_ASSET.matches((it["name"] as? JsonPrimitive)?.contentOrNull ?: "") }
            ?: throw SetupError("Couldn't find Python for this phone.")
        val name = (asset["name"] as JsonPrimitive).content
        val url = (asset["browser_download_url"] as JsonPrimitive).content
        if (!url.startsWith("https://github.com/astral-sh/python-build-standalone/")) throw SetupError("Unexpected Python download address.")
        val sha = (asset["digest"] as? JsonPrimitive)?.contentOrNull?.removePrefix("sha256:")?.takeIf { it.length == 64 }
            ?: text("https://github.com/astral-sh/python-build-standalone/releases/download/$PBS_RELEASE/SHA256SUMS")
                .lines().firstOrNull { it.trim().endsWith(name) }?.substringBefore(' ')
            ?: throw SetupError("Couldn't check the Python download.")
        val file = File(downloads, "python.tar.gz")
        download(url, file, sha) { progress.step(Stage.Python, it * 8 / 10) }
        val stage = File(rootfs, "opt/.python.partial")
        RootfsExtractor.deleteTree(stage)
        file.inputStream().use { RootfsExtractor.extract(it, stage) }
        file.delete()
        val py = File(stage, "python")
        val lib = File(py, "lib/python$PYTHON")
        for (d in listOf("test", "idlelib", "tkinter", "turtledemo", "lib2to3/tests", "unittest/test")) RootfsExtractor.deleteTree(File(lib, d))
        File(py, "lib").listFiles()?.filter { it.name.startsWith("libtcl") || it.name.startsWith("libtk") || it.name.matches(Regex("(tcl|tk)\\d.*")) }
            ?.forEach { RootfsExtractor.deleteTree(it) }
        val dest = File(rootfs, "opt/python")
        RootfsExtractor.deleteTree(dest)
        if (!py.renameTo(dest)) throw SetupError("Couldn't finish unpacking Python.")
        RootfsExtractor.deleteTree(stage)
        progress.step(Stage.Python, 100)
    }

    // ---- Orbit's scripts and Google's server ----

    /** Orbit's own launcher scripts, from the APK (rewritten each time, so an update reaches them). */
    fun installTools() {
        val dir = File(rootfs, "opt/orbit").apply { mkdirs() }
        for (name in TOOLS) {
            val f = File(dir, name)
            context.assets.open("gemini/$name").use { input -> f.outputStream().use { input.copyTo(it) } }
            f.setExecutable(true, false)
            f.setReadable(true, false)
        }
        File(rootfs, "run/orbit").mkdirs()
        File(rootfs, "root/work").mkdirs()
    }

    private suspend fun installServer(progress: Progress) {
        progress.step(Stage.Server, 0)
        val reg = Json.parseToJsonElement(text(REGISTRY)).jsonObject
        val version = (reg["version"] as? JsonPrimitive)?.contentOrNull ?: throw SetupError("Couldn't read Google's server listing.")
        if (ready && installedVersion == version) { progress.step(Stage.Prepare, 100); return }
        val art = reg["distribution"]?.jsonObject?.get("binary")?.jsonObject?.get("linux-aarch64")?.jsonObject
            ?: throw SetupError("Google's server has no build for this phone.")
        val url = (art["archive"] as? JsonPrimitive)?.contentOrNull ?: throw SetupError("Google's server has no download.")
        val sha = (art["sha256"] as? JsonPrimitive)?.contentOrNull ?: throw SetupError("Google's server has no checksum.")
        if (!url.startsWith("https://dl.google.com/")) throw SetupError("Unexpected download address for Google's server.")
        val zip = host("/root/agy/download.zip").apply { parentFile?.mkdirs() }
        download(url, zip, sha) { progress.step(Stage.Server, it) }
        progress.step(Stage.Prepare, 0)
        val p = start(listOf("/opt/python/bin/python3", "-u", "/opt/orbit/orbit_prepare.py", "--zip", "/root/agy/download.zip", "--version", version))
        p.outputStream.close()
        val tail = ArrayDeque<String>()
        val out = Thread { runCatching { p.inputStream.bufferedReader().forEachLine { synchronized(tail) { tail.addLast(it); if (tail.size > 20) tail.removeFirst() } } } }.apply { start() }
        p.errorStream.bufferedReader().forEachLine { line ->
            synchronized(tail) { tail.addLast(line); if (tail.size > 20) tail.removeFirst() }
            STAGE.find(line)?.let { m ->
                val pct = m.groupValues[2].toIntOrNull()
                val base = when (m.groupValues[1]) { "extract" -> 0; "install" -> 40; "smoke" -> 90; "done" -> 100; else -> null }
                if (base != null) progress.step(Stage.Prepare, if (pct != null && base < 90) base + pct * 40 / 100 else base)
            }
        }
        val code = p.waitFor()
        out.join(2000)
        zip.delete()
        if (code != 0 || !ready) {
            val why = synchronized(tail) { tail.lastOrNull { it.isNotBlank() && !it.contains("STAGE") } } ?: "exit $code"
            throw SetupError("Setting up Google's server failed: ${why.take(300)}")
        }
    }

    // ---- running things in the Linux ----

    /** Starts [command] in the Linux with stdin/stdout/stderr pipes. */
    fun start(command: List<String>, extraEnv: Map<String, String> = emptyMap()): Process {
        tmp.mkdirs()
        val r = rootfs.path
        val argv = buildList {
            add(File(nativeDir, "libproot.so").path)
            addAll(listOf("--kill-on-exit", "--link2symlink", "-0", "-r", r))
            addAll(ProotDevices.bindArgs())
            addAll(listOf("-b", "/proc", "-b", "/sys", "-b", "$r/tmp:/dev/shm", "-w", "/root"))
            add("/usr/bin/env"); add("-i")
            for ((k, v) in guestEnv() + extraEnv) add("$k=$v")
            addAll(command)
        }
        return ProcessBuilder(argv).apply {
            environment().clear()
            environment()["PROOT_LOADER"] = File(nativeDir, "libproot_loader.so").path
            environment()["PROOT_TMP_DIR"] = tmp.path
        }.start()
    }

    private fun guestEnv() = mapOf(
        "HOME" to "/root", "USER" to "root", "LANG" to "C.UTF-8", "TMPDIR" to "/tmp",
        "PATH" to ProcRunner.PATH, "TZ" to ProcRunner.timezone(),
    )

    // ---- downloads ----

    private fun text(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "Orbit").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw SetupError("Download failed (${r.code}) from ${host(url)}.")
            return r.body?.string() ?: ""
        }
    }

    private suspend fun download(url: String, dest: File, sha256: String, onPercent: (Int) -> Unit) {
        val req = Request.Builder().url(url).header("User-Agent", "Orbit").build()
        val digest = MessageDigest.getInstance("SHA-256")
        dest.parentFile?.mkdirs()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw SetupError("Download failed (${r.code}) from ${host(url)}.")
            val body = r.body ?: throw SetupError("Empty download from ${host(url)}.")
            val total = body.contentLength()
            var done = 0L
            var last = -1
            body.byteStream().use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); digest.update(buf, 0, n); done += n
                        if (total > 0) { val pct = (done * 100 / total).toInt(); if (pct != last) { last = pct; onPercent(pct) } }
                    }
                }
            }
        }
        val got = digest.digest().joinToString("") { "%02x".format(it) }
        if (!got.equals(sha256, ignoreCase = true)) {
            dest.delete()
            throw SetupError("The download from ${host(url)} didn't match its checksum.")
        }
    }

    private fun host(url: String) = runCatching { java.net.URI(url).host }.getOrNull() ?: url

    companion object {
        const val REGISTRY = "https://raw.githubusercontent.com/agentclientprotocol/registry/main/antigravity-acp/agent.json"
        /** The python-build-standalone release openbot-local runs Antigravity on. */
        const val PBS_RELEASE = "20261026"
        const val PYTHON = "3.14"
        private val PBS_ASSET = Regex("^cpython-3\\.14\\.\\d+\\+$PBS_RELEASE-aarch64-unknown-linux-gnu-install_only_stripped\\.tar\\.gz$")
        private val BASE = Regex("^([0-9a-f]{64}) \\*?(ubuntu-base-24\\.04\\.\\d+-base-arm64\\.tar\\.gz)$")
        private val STAGE = Regex("\\[orbit] STAGE (\\w+)(?: (\\d+))?")
        private const val MB = 1024L * 1024
        /** Peak while setting up: Google's download and its unpacked files side by side, plus the venv. */
        const val NEEDED_FREE = 1200L * MB
        val TOOLS = listOf("orbit_prepare.py", "orbit_agy.py", "agy_acp_server", "open-url", "orbit_mcp_bridge.py")
    }
}
