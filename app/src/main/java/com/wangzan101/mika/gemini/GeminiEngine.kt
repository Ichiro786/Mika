package com.wangzan101.mika.gemini

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import com.wangzan101.mika.BuildConfig
import com.wangzan101.mika.core.acp.AcpBackend
import com.wangzan101.mika.core.acp.AcpConnection
import com.wangzan101.mika.core.acp.AcpError
import com.wangzan101.mika.core.acp.AcpPhoneTools
import com.wangzan101.mika.core.acp.McpLineHandler
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Google's Antigravity ACP server, running in [GeminiRuntime]'s Linux, and Orbit's ACP client for it.
 *
 * Orbit names itself honestly in `clientInfo` ("orbit"). Antigravity offers its Claude and GPT models only
 * to a few named editors, so Orbit gets the Gemini models of the user's Google plan, which is all it asks for.
 * Sign-in is Google's own `oauth-personal` in the official server; Orbit only opens the link it prints.
 */
class GeminiEngine private constructor(private val context: Context) : AcpBackend {
    val runtime = GeminiRuntime(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var process: Process? = null
    private var connection: AcpConnection? = null
    private val sessions = ConcurrentHashMap<String, String>()          // thread -> ACP session
    private val listeners = ConcurrentHashMap<String, (JsonObject) -> Unit>()
    private val turnTools = ConcurrentHashMap<String, AcpPhoneTools>()   // thread -> this turn's phone tools
    private val sockets = ConcurrentHashMap<String, LocalServerSocket>()
    private var idleStop: Job? = null
    private var socketCount = 0
    private val prefs = context.getSharedPreferences("gemini_plan", Context.MODE_PRIVATE)

    private val _signInUrl = MutableStateFlow<String?>(null)
    /** The Google sign-in link the server asked to open, while a sign-in waits. */
    val signInUrl: StateFlow<String?> = _signInUrl

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    /** Tasks run on the Gemini plan instead of Orbit's own models. */
    val enabled: StateFlow<Boolean> = _enabled
    fun setEnabled(on: Boolean) { prefs.edit().putBoolean(KEY_ENABLED, on).apply(); _enabled.value = on }

    private val _signedIn = MutableStateFlow(prefs.getBoolean(KEY_SIGNED_IN, false))
    val signedIn: StateFlow<Boolean> = _signedIn
    private fun markSignedIn(on: Boolean) { prefs.edit().putBoolean(KEY_SIGNED_IN, on).apply(); _signedIn.value = on }

    /** Tasks go to Gemini: switched on, set up and signed in. */
    val active: Boolean get() = _enabled.value && _signedIn.value && runtime.ready

    // ---- setup and sign-in, kept here so they carry on when the settings page closes ----

    data class SetupProgress(val stage: GeminiRuntime.Stage, val percent: Int)

    private val _installed = MutableStateFlow(runtime.ready)
    val installed: StateFlow<Boolean> = _installed
    private val _setup = MutableStateFlow<SetupProgress?>(null)
    /** Setup under way, or null. */
    val setupProgress: StateFlow<SetupProgress?> = _setup
    private val _setupError = MutableStateFlow<String?>(null)
    val setupError: StateFlow<String?> = _setupError
    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn
    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError

    fun startSetup() {
        if (_setup.value != null) return
        _setupError.value = null
        _setup.value = SetupProgress(GeminiRuntime.Stage.Space, 0)
        scope.launch {
            try {
                lock.withLock { stopLocked() } // a newer server version may replace the running one
                runtime.setup { s, p -> _setup.value = SetupProgress(s, p) }
            } catch (e: Throwable) {
                Log.w(TAG, "setup failed", e)
                _setupError.value = e.message ?: e.javaClass.simpleName
            } finally {
                _installed.value = runtime.ready
                _setup.value = null
            }
        }
    }

    fun startSignIn() {
        if (_signingIn.value) { signInUrl.value?.let(::openLink); return }
        _signingIn.value = true
        _signInError.value = null
        scope.launch {
            try {
                signIn(::openLink)
            } catch (e: Throwable) {
                Log.w(TAG, "sign-in failed", e)
                _signInError.value = e.message ?: e.javaClass.simpleName
            } finally {
                _signingIn.value = false
            }
        }
    }

    /** Opens the Google sign-in page in the phone's browser. Only Google's own sign-in pages are opened. */
    fun openLink(url: String) {
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        if (!url.startsWith("https://") || host == null || !(host == "accounts.google.com" || host.endsWith(".google.com"))) return
        runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "can't open sign-in page", it) }
    }

    /** Last lines the server wrote on stderr (for error messages). */
    private val stderrTail = ArrayDeque<String>()

    // ---- process ----

    private suspend fun ensureStarted(): AcpConnection = lock.withLock {
        connection?.takeIf { !it.closed && process?.isAlive == true }?.let { return it }
        stopLocked()
        if (!runtime.ready) throw AcpError(-32000, "The Gemini plan isn't set up yet: open Settings, Gemini plan.")
        runtime.installTools()
        runtime.host("/run/orbit/signin-url").delete()
        val p = runtime.start(listOf("/root/agy/current/bin/agy_acp_server", "--uid="))
        process = p
        scope.launch { readStderr(p) }
        scope.launch { watchSignInFile(p) }
        val c = AcpConnection(p.inputStream, p.outputStream, scope, ::onNotification, ::onRequest, onClosed = { onClosed(p) })
        connection = c
        c.request("initialize", buildJsonObject {
            put("protocolVersion", 1)
            put("clientCapabilities", buildJsonObject {
                put("fs", buildJsonObject { put("readTextFile", false); put("writeTextFile", false) })
                put("terminal", false)
            })
            put("clientInfo", buildJsonObject { put("name", "orbit"); put("title", "Orbit"); put("version", BuildConfig.VERSION_NAME) })
        }, timeoutMs = 180_000)
        c
    }

    private fun onClosed(p: Process) {
        if (process === p) {
            sessions.clear()
            closeSockets()
        }
    }

    private fun readStderr(p: Process) = runCatching {
        p.errorStream.bufferedReader().forEachLine { line ->
            synchronized(stderrTail) { stderrTail.addLast(line.take(500)); if (stderrTail.size > 30) stderrTail.removeFirst() }
            SIGN_IN.find(line)?.let { _signInUrl.value = it.value }
        }
    }

    private suspend fun watchSignInFile(p: Process) {
        val f = runtime.host("/run/orbit/signin-url")
        while (p.isAlive) {
            if (f.exists()) runCatching { f.readText().trim().substringAfter(' ').takeIf { it.startsWith("https://") }?.let { _signInUrl.value = it } }
            delay(1000)
        }
    }

    fun stop() { scope.launch { lock.withLock { stopLocked() } } }

    private fun stopLocked() {
        connection?.close(); connection = null
        process?.let { it.destroy(); scope.launch { delay(3000); if (it.isAlive) it.destroyForcibly() } }
        process = null
        sessions.clear()
        closeSockets()
    }

    private fun closeSockets() {
        sockets.values.forEach { runCatching { it.close() } }
        sockets.clear()
    }

    // ---- sign-in ----

    /** Google sign-in for the user's plan. [onLink] gets the link to open in the phone's browser. */
    suspend fun signIn(onLink: (String) -> Unit) {
        _signInUrl.value = null
        val c = ensureStarted()
        val watcher = scope.launch { signInUrl.collect { it?.let(onLink) } }
        try {
            c.request("authenticate", buildJsonObject { put("methodId", "oauth-personal") }, timeoutMs = 6 * 60_000)
            markSignedIn(true)
        } catch (e: AcpError) {
            throw AcpError(e.code, "Google sign-in didn't finish: ${e.message}")
        } finally {
            watcher.cancel()
            _signInUrl.value = null
        }
    }

    fun signOut() {
        markSignedIn(false)
        scope.launch {
            lock.withLock { stopLocked() }
            // Google's token file lives in a hidden folder of the Linux's home; removing them signs the server out.
            runtime.host("/root").listFiles()?.filter { it.name.startsWith(".") }
                ?.forEach { com.wangzan101.mika.core.cap.sandbox.RootfsExtractor.deleteTree(it) }
        }
    }

    fun removeAll() {
        setEnabled(false); markSignedIn(false)
        scope.launch { lock.withLock { stopLocked() }; runtime.remove(); _installed.value = runtime.ready }
    }

    // ---- AcpBackend ----

    override suspend fun session(threadId: String, tools: AcpPhoneTools): Pair<String, Boolean> {
        idleStop?.cancel()
        val c = ensureStarted()
        turnTools[threadId] = tools
        sessions[threadId]?.let { return it to false }
        val guest = openSocket(threadId)
        val result = c.request("session/new", buildJsonObject {
            put("cwd", "/root/work")
            put("mcpServers", buildJsonArray {
                add(buildJsonObject {
                    put("name", "orbit-phone")
                    put("command", "/opt/python/bin/python3")
                    put("args", buildJsonArray { add(JsonPrimitive("-I")); add(JsonPrimitive("/opt/orbit/orbit_mcp_bridge.py")) })
                    put("env", buildJsonArray { add(buildJsonObject { put("name", "ORBIT_MCP_SOCKET"); put("value", guest) }) })
                })
            })
            // Antigravity's own scheduler and workflows run agents Orbit doesn't show.
            put("_meta", buildJsonObject { put("antigravity", buildJsonObject {
                put("disabledTools", buildJsonArray { add(JsonPrimitive("schedule")); add(JsonPrimitive("run_workflow")) })
            }) })
        }, timeoutMs = 300_000)
        val id = (result as? JsonObject)?.get("sessionId")?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: throw AcpError(-32603, "Gemini didn't open a session")
        markSignedIn(true)
        sessions[threadId] = id
        return id to true
    }

    override suspend fun prompt(sessionId: String, prompt: JsonArray, onUpdate: (JsonObject) -> Unit): String {
        val c = ensureStarted()
        listeners[sessionId] = onUpdate
        try {
            val r = c.request("session/prompt", buildJsonObject { put("sessionId", sessionId); put("prompt", prompt) })
            return ((r as? JsonObject)?.get("stopReason") as? JsonPrimitive)?.contentOrNull ?: "end_turn"
        } catch (e: AcpError) {
            val tail = synchronized(stderrTail) { stderrTail.lastOrNull { it.isNotBlank() } }
            if (c.closed && tail != null) throw AcpError(e.code, "${e.message} ($tail)")
            throw e
        } finally {
            listeners.remove(sessionId)
        }
    }

    override fun cancel(sessionId: String) {
        connection?.notify("session/cancel", buildJsonObject { put("sessionId", sessionId) })
    }

    override fun release(threadId: String) {
        turnTools.remove(threadId)
        idleStop?.cancel()
        idleStop = scope.launch {
            delay(IDLE_STOP_MS)
            if (turnTools.isEmpty()) lock.withLock { stopLocked() }
        }
    }

    private suspend fun onNotification(method: String, params: JsonObject) {
        if (method != "session/update") return
        val sid = (params["sessionId"] as? JsonPrimitive)?.contentOrNull ?: return
        val update = params["update"] as? JsonObject ?: return
        listeners[sid]?.invoke(update)
    }

    private suspend fun onRequest(method: String, params: JsonObject): JsonElement = when (method) {
        // The server's own tools run in its Linux, away from the phone; phone steps go through Orbit's guards.
        "session/request_permission" -> {
            val options = (params["options"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
            val pick = options.firstOrNull { (it["kind"] as? JsonPrimitive)?.contentOrNull == "allow_once" }
                ?: options.firstOrNull { (it["kind"] as? JsonPrimitive)?.contentOrNull?.startsWith("allow") == true }
            buildJsonObject {
                put("outcome", buildJsonObject {
                    if (pick != null) { put("outcome", "selected"); put("optionId", pick["optionId"]!!) } else put("outcome", "cancelled")
                })
            }
        }
        else -> throw AcpError(-32601, "Method not found: $method")
    }

    // ---- phone tools socket ----

    /** A Unix socket in the Linux's /run/orbit for this chat's MCP bridge; returns its guest path. */
    private fun openSocket(threadId: String): String {
        val name = "m${++socketCount}.sock"
        val hostFile = runtime.host("/run/orbit/$name").apply { parentFile?.mkdirs(); delete() }
        val bound = LocalSocket(LocalSocket.SOCKET_STREAM)
        bound.bind(LocalSocketAddress(hostFile.path, LocalSocketAddress.Namespace.FILESYSTEM))
        val server = LocalServerSocket(bound.fileDescriptor)
        sockets[threadId]?.let { runCatching { it.close() } }
        sockets[threadId] = server
        val handler = McpLineHandler(BuildConfig.VERSION_NAME) { turnTools[threadId] }
        scope.launch {
            while (true) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                scope.launch { serve(client, handler) }
            }
        }
        return "/run/orbit/$name"
    }

    private suspend fun serve(client: LocalSocket, handler: McpLineHandler) {
        val out = client.outputStream
        val writeLock = Mutex()
        runCatching {
            client.inputStream.bufferedReader().forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                scope.launch {
                    val answer = runCatching { handler.handle(line) }.getOrElse { Log.w(TAG, "mcp call failed", it); null } ?: return@launch
                    writeLock.withLock { runCatching { out.write((answer + "\n").toByteArray()); out.flush() } }
                }
            }
        }
        runCatching { client.close() }
    }

    companion object {
        private const val TAG = "OrbitGemini"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SIGNED_IN = "signed_in"
        private const val IDLE_STOP_MS = 10 * 60_000L
        private val SIGN_IN = Regex("https://accounts\\.google\\.com/\\S+")

        @Volatile private var instance: GeminiEngine? = null
        fun get(context: Context): GeminiEngine = instance ?: synchronized(this) {
            instance ?: GeminiEngine(context.applicationContext).also { instance = it }
        }
    }
}
