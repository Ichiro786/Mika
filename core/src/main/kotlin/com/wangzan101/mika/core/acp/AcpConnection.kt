package com.wangzan101.mika.core.acp

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** An error answer from the agent, or a broken channel. */
class AcpError(val code: Int, message: String, val data: JsonElement? = null) : Exception(message)

/**
 * JSON-RPC 2.0 over newline-delimited JSON, the transport of the Agent Client Protocol (ACP).
 * [onNotification] gets the agent's notifications (session/update); [onRequest] answers the agent's
 * requests (session/request_permission) and may throw [AcpError].
 */
class AcpConnection(
    input: InputStream,
    private val output: OutputStream,
    private val scope: CoroutineScope,
    private val onNotification: suspend (method: String, params: JsonObject) -> Unit,
    private val onRequest: suspend (method: String, params: JsonObject) -> JsonElement,
    private val onClosed: (Throwable?) -> Unit = {},
) {
    private val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
    private val ids = AtomicLong(1)
    private val writeLock = Any()
    @Volatile var closed = false
        private set

    private val readJob: Job = scope.launch(Dispatchers.IO) {
        var cause: Throwable? = null
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val msg = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: continue
                dispatch(msg)
            }
        } catch (t: Throwable) {
            cause = t
        } finally {
            closed = true
            val err = AcpError(-32000, "The Gemini engine stopped" + (cause?.message?.let { ": $it" } ?: ""))
            pending.values.forEach { it.completeExceptionally(err) }
            pending.clear()
            onClosed(cause)
        }
    }

    private fun dispatch(msg: JsonObject) {
        val method = (msg["method"] as? JsonPrimitive)?.contentOrNull
        val id = msg["id"]
        if (method == null) {
            val key = (id as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: return
            val waiter = pending.remove(key) ?: return
            val error = msg["error"] as? JsonObject
            if (error != null) {
                waiter.completeExceptionally(AcpError(
                    (error["code"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: -32603,
                    (error["message"] as? JsonPrimitive)?.contentOrNull ?: "Agent error",
                    error["data"],
                ))
            } else waiter.complete(msg["result"] ?: JsonNull)
            return
        }
        val params = msg["params"] as? JsonObject ?: JsonObject(emptyMap())
        if (id == null || id is JsonNull) {
            scope.launch { runCatching { onNotification(method, params) } }
            return
        }
        scope.launch {
            val answer = try {
                buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", onRequest(method, params)) }
            } catch (e: AcpError) {
                errorAnswer(id, e.code, e.message ?: "error")
            } catch (e: Exception) {
                errorAnswer(id, -32603, e.message ?: "error")
            }
            send(answer)
        }
    }

    private fun errorAnswer(id: JsonElement, code: Int, message: String) = buildJsonObject {
        put("jsonrpc", "2.0"); put("id", id)
        put("error", buildJsonObject { put("code", code); put("message", message) })
    }

    private fun send(msg: JsonObject) {
        val bytes = (json.encodeToString(JsonObject.serializer(), msg) + "\n").toByteArray(Charsets.UTF_8)
        synchronized(writeLock) {
            output.write(bytes)
            output.flush()
        }
    }

    suspend fun request(method: String, params: JsonObject, timeoutMs: Long = 0): JsonElement {
        if (closed) throw AcpError(-32000, "The Gemini engine is not running")
        val id = ids.getAndIncrement()
        val waiter = CompletableDeferred<JsonElement>()
        pending[id] = waiter
        try {
            send(buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("method", method); put("params", params) })
        } catch (e: Exception) {
            pending.remove(id)
            throw AcpError(-32000, "Could not reach the Gemini engine: ${e.message}")
        }
        return try {
            if (timeoutMs > 0) withTimeout(timeoutMs) { waiter.await() } else waiter.await()
        } finally {
            pending.remove(id)
        }
    }

    fun notify(method: String, params: JsonObject) {
        if (closed) return
        runCatching { send(buildJsonObject { put("jsonrpc", "2.0"); put("method", method); put("params", params) }) }
    }

    fun close() {
        closed = true
        runCatching { output.close() }
        readJob.cancel()
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}
