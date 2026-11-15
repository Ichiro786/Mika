package com.wangzan101.mika.core.acp

import com.wangzan101.mika.core.action.ActionExecutor
import com.wangzan101.mika.core.action.ActionOutcome
import com.wangzan101.mika.core.action.ActionRecorder
import com.wangzan101.mika.core.action.ActivityRecord
import com.wangzan101.mika.core.action.ApprovalSettings
import com.wangzan101.mika.core.action.Origin
import com.wangzan101.mika.core.action.execution
import com.wangzan101.mika.core.guard.OperationGuards
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.run.RunEventBus
import com.wangzan101.mika.core.run.RunProvenance
import com.wangzan101.mika.core.tool.ToolDispatcher
import java.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Orbit's phone tools for an outside agent (Gemini through Antigravity). Every call goes through the same
 * [ActionExecutor] and guards as Orbit's own steps, with the user's ask/autonomous setting. There is no
 * confirmation gate here, so a step that would need the user's confirmation (a payment) is refused.
 */
class AcpPhoneTools internal constructor(
    private val tools: ToolDispatcher,
    approvals: ApprovalSettings,
    provenance: RunProvenance,
    autonomous: Boolean,
    selfPackage: String?,
    private val events: RunEventBus,
    private val clock: () -> Long,
    /** Called before each tool runs, with its name (for "what Orbit is doing" lines). */
    private val onCall: (step: Int, tool: String) -> Unit,
) {
    private val lock = Mutex()
    private var step = 0
    private val executor = ActionExecutor(
        tools, OperationGuards(), null, gate = {},
        recorder = ActionRecorder(events, clock), offScreen = tools.offScreenGuard(), approvals = approvals,
        provenance = provenance, selfPackage = selfPackage, autonomous = autonomous,
    )

    fun definitions() = tools.definitions()

    /** MCP `tools/call` result: text content (JSON of the tool's result) and the screen picture if there is one. */
    suspend fun call(name: String, args: JsonObject): JsonObject = lock.withLock {
        if (definitions().none { it.name == name }) return@withLock mcpText("""{"ok":false,"error":"unknown_tool"}""", true)
        step++
        val callId = "acp_$step"
        onCall(step, name)
        val outcome = executor.run(Part.ToolCall(callId, name, args.toString()), Origin.McpTool(step))
        val exec = outcome.execution()
        if (outcome is ActionOutcome.Ran) {
            ActivityRecord.of(tools, name, args, exec, step, "gemini", callId, clock())?.let(events::event)
        }
        val failed = exec.content["ok"] == JsonPrimitive(false) || outcome !is ActionOutcome.Ran
        buildJsonObject {
            put("content", buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", exec.content.toString()) })
                exec.images.firstOrNull()?.let { img ->
                    add(buildJsonObject {
                        put("type", "image")
                        put("data", Base64.getEncoder().encodeToString(img))
                        put("mimeType", mime(img))
                    })
                }
            })
            put("isError", failed)
        }
    }

    private fun mime(b: ByteArray): String = when {
        b.size > 3 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() -> "image/png"
        b.size > 3 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() -> "image/webp"
        else -> "image/jpeg"
    }

    companion object {
        fun create(
            tools: ToolDispatcher, approvals: ApprovalSettings, provenance: RunProvenance, autonomous: Boolean,
            selfPackage: String?, events: RunEventBus, clock: () -> Long = System::currentTimeMillis,
            onCall: (Int, String) -> Unit = { _, _ -> },
        ) = AcpPhoneTools(tools, approvals, provenance, autonomous, selfPackage, events, clock, onCall)

        internal fun mcpText(text: String, error: Boolean) = buildJsonObject {
            put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", text) }) })
            put("isError", error)
        }
    }
}

/**
 * The MCP side of the stdio bridge: one JSON-RPC line in, one line out (null for notifications).
 * [tools] is looked up per call, so a session's bridge reaches the phone tools of the turn running now.
 */
class McpLineHandler(private val serverVersion: String, private val tools: () -> AcpPhoneTools?) {
    suspend fun handle(line: String): String? {
        val msg = runCatching { AcpConnection.json.parseToJsonElement(line) as? JsonObject }.getOrNull()
            ?: return error(null, -32700, "Parse error")
        val method = (msg["method"] as? JsonPrimitive)?.contentOrNull ?: return null
        val id = msg["id"] ?: return null
        val params = msg["params"] as? JsonObject ?: JsonObject(emptyMap())
        val result: JsonElement = when (method) {
            "initialize" -> buildJsonObject {
                put("protocolVersion", (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull ?: "2025-06-18")
                put("capabilities", buildJsonObject { put("tools", buildJsonObject {}) })
                put("serverInfo", buildJsonObject { put("name", "orbit-phone"); put("version", serverVersion) })
                put("instructions", INSTRUCTIONS)
            }
            "ping" -> buildJsonObject {}
            "tools/list" -> buildJsonObject {
                put("tools", JsonArray((tools()?.definitions() ?: emptyList()).map { spec ->
                    buildJsonObject {
                        put("name", spec.name)
                        put("description", spec.description)
                        put("inputSchema", runCatching { AcpConnection.json.parseToJsonElement(spec.parametersJsonSchema) }
                            .getOrNull() as? JsonObject ?: buildJsonObject { put("type", "object") })
                    }
                }))
            }
            "tools/call" -> {
                val name = (params["name"] as? JsonPrimitive)?.contentOrNull ?: return error(id, -32602, "Missing tool name")
                val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                val t = tools() ?: return reply(id, AcpPhoneTools.mcpText("""{"ok":false,"error":"no_task_running"}""", true))
                t.call(name, args)
            }
            else -> return error(id, -32601, "Method not found: $method")
        }
        return reply(id, result)
    }

    private fun reply(id: JsonElement, result: JsonElement) = buildJsonObject {
        put("jsonrpc", "2.0"); put("id", id); put("result", result)
    }.toString()

    private fun error(id: JsonElement?, code: Int, message: String) = buildJsonObject {
        put("jsonrpc", "2.0"); put("id", id ?: kotlinx.serialization.json.JsonNull)
        put("error", buildJsonObject { put("code", code); put("message", message) })
    }.toString()

    companion object {
        const val INSTRUCTIONS = "These tools act on the user's Android phone through Orbit. Read the screen before acting, " +
            "act one step at a time, and never pay or confirm purchases: such steps are refused."
    }
}
