package com.wangzan101.mika.core.acp

import com.wangzan101.mika.core.action.ConfirmChoice
import com.wangzan101.mika.core.agent.FailKind
import com.wangzan101.mika.core.tool.HandBack
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.agent.Turn
import com.wangzan101.mika.core.record.RunEvent
import com.wangzan101.mika.core.run.RunEventBus
import com.wangzan101.mika.core.run.RunInbox
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** What a turn needs from the running Antigravity server (the app owns the process and the sessions). */
interface AcpBackend {
    /** The ACP session of [threadId] (made on first use, with Orbit's phone tools as an MCP server); true when it is new. */
    suspend fun session(threadId: String, tools: AcpPhoneTools): Pair<String, Boolean>
    /** session/prompt; [onUpdate] gets each session/update's `update` object. Returns the stop reason. */
    suspend fun prompt(sessionId: String, prompt: JsonArray, onUpdate: (JsonObject) -> Unit): String
    fun cancel(sessionId: String)
    /** The turn is over: the session's phone tools are gone until the next turn. */
    fun release(threadId: String)
}

/**
 * One chat turn run by Gemini through the Antigravity ACP server instead of Orbit's own loop. Publishes
 * [TaskState]s into [state] and tool activity into [events], like AgentLoop does.
 */
class AcpTurn(
    private val backend: AcpBackend,
    private val tools: AcpPhoneTools,
    private val events: RunEventBus,
    private val state: MutableStateFlow<TaskState>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val answer = StringBuilder()
    private var step = 0
    private var note = ""
    private var tool: String? = null
    private var narration: String? = null
    private val stop = CompletableDeferred<Unit>()

    suspend fun run(threadId: String, goal: String, priorTurns: List<Turn>, background: String?, inbox: RunInbox): TaskState {
        inbox.bind(object : RunInbox.Handler {
            override suspend fun answer(text: String, askId: String?) = false
            override suspend fun confirm(choice: ConfirmChoice, askId: String?) = false
            override suspend fun handBack(result: HandBack, askId: String?) = false
            override fun cancel() { stop.complete(Unit) }
        })
        events.event(RunEvent.TaskStarted(clock(), goal, "gemini-plan"))
        publish()
        return try {
            coroutineScope {
                val (sid, fresh) = backend.session(threadId, tools)
                val text = if (fresh) contextFor(goal, priorTurns, background) else goal
                val prompt = buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", text) }) }
                val turn = async { backend.prompt(sid, prompt, ::onUpdate) }
                val stopped = async { stop.await() }
                val reason = select<String?> {
                    turn.onAwait { it }
                    stopped.onAwait { null }
                }
                if (reason == null) {
                    backend.cancel(sid)
                    turn.cancel()
                    return@coroutineScope TaskState.Cancelled
                }
                stopped.cancel()
                end(reason)
            }
        } catch (e: AcpError) {
            TaskState.Failed(failure(e.message ?: "Gemini error"), FailKind.Model)
        } finally {
            backend.release(threadId)
        }.also { events.event(RunEvent.TaskEnded(clock(), outcomeOf(it), (it as? TaskState.Succeeded)?.answer ?: (it as? TaskState.Failed)?.reason ?: "")) }
    }

    private fun end(reason: String): TaskState {
        val text = answer.toString().trim()
        QUOTA.find(text)?.let { return TaskState.Failed("Your Gemini plan's usage limit is reached. ${it.groupValues[1].trim()}".trim(), FailKind.TokenLimit) }
        if (EXECUTION_ERROR.containsMatchIn(text) && text.length < 2000) return TaskState.Failed(failure(text), FailKind.Model)
        return when {
            reason == "cancelled" -> TaskState.Cancelled
            text.isNotEmpty() -> TaskState.Succeeded(text, verified = false, reviewed = false)
            reason == "refusal" -> TaskState.Failed("Gemini declined this request.", FailKind.GaveUp)
            reason == "max_tokens" || reason == "max_turn_requests" -> TaskState.Failed("Gemini stopped before finishing (limit reached).", FailKind.StepLimit)
            else -> TaskState.Succeeded("Done.", verified = false, reviewed = false)
        }
    }

    private fun onUpdate(u: JsonObject) {
        when ((u["sessionUpdate"] as? JsonPrimitive)?.contentOrNull) {
            "agent_message_chunk" -> textOf(u)?.let { answer.append(it); publish() }
            "agent_thought_chunk" -> textOf(u)?.let {
                narration = ((narration ?: "") + it).takeLast(400)
                publish()
            }
            "tool_call" -> {
                val title = (u["title"] as? JsonPrimitive)?.contentOrNull ?: "Working"
                step++
                tool = title
                note = title
                narration = null
                // Phone tools record their own activity in AcpPhoneTools; the server's own tools are listed here.
                if (!isPhoneTool(title)) {
                    val kind = (u["kind"] as? JsonPrimitive)?.contentOrNull
                    events.event(RunEvent.Activity(clock(), step, if (kind in READS) "observe" else "act", RunEvent.clip(title),
                        source = "gemini", callId = (u["toolCallId"] as? JsonPrimitive)?.contentOrNull))
                }
                publish()
            }
            "tool_call_update" -> {
                val status = (u["status"] as? JsonPrimitive)?.contentOrNull
                if (status == "completed" || status == "failed") { tool = null; publish() }
            }
        }
    }

    /** Called by the phone tools before each phone step. */
    fun phoneStep(name: String) {
        step++
        tool = name
        note = name
        publish()
    }

    private fun isPhoneTool(title: String) = tools.definitions().any { title.contains(it.name) } || title.contains("orbit-phone")

    private fun publish() {
        if (state.value is TaskState.Terminal) return
        state.value = TaskState.Running(step, note, tool = tool, narration = narration?.trim()?.lines()?.lastOrNull { it.isNotBlank() },
            preview = answer.toString().takeIf { it.isNotBlank() })
    }

    private fun textOf(u: JsonObject): String? {
        val c = u["content"] as? JsonObject ?: return null
        return (c["text"] as? JsonPrimitive)?.contentOrNull
    }

    private fun failure(detail: String): String {
        val d = detail.replace(Regex("^(?:RequestError:\\s*)?Internal error:\\s*"), "").trim()
        return when {
            Regex("RESOURCE_EXHAUSTED|\\b429\\b|rate.?limit|quota", RegexOption.IGNORE_CASE).containsMatchIn(d) ->
                "Gemini is busy or your plan's limit is reached. Try again later. ($d)".take(600)
            Regex("auth|sign.?in|credential|unauthori[sz]ed|401", RegexOption.IGNORE_CASE).containsMatchIn(d) ->
                "Gemini needs you to sign in again: open Settings, Gemini plan, Sign in with Google."
            else -> "Gemini couldn't finish: $d".take(600)
        }
    }

    private fun outcomeOf(s: TaskState) = when (s) {
        is TaskState.Succeeded -> "succeeded"
        is TaskState.Failed -> "failed"
        TaskState.Cancelled -> "cancelled"
        else -> "ended"
    }

    companion object {
        private val READS = setOf("read", "search", "fetch", "think")
        private val QUOTA = Regex("^\\s*Usage Limit Reached\\s*\\n\\n([\\s\\S]*)$")
        private val EXECUTION_ERROR = Regex("^\\s*Agent execution error: ")

        /** The first prompt of a new session: a little of this chat, then the request. */
        fun contextFor(goal: String, priorTurns: List<Turn>, background: String?): String = buildString {
            append("You are working for the user through Orbit, an Android phone agent. ")
            append("Use the orbit-phone tools to look at and operate the phone when the request needs it. ")
            append("Answer in the user's language, briefly.\n")
            if (!background.isNullOrBlank()) append("\nWhat Orbit knows about the user:\n").append(background.take(4000)).append('\n')
            val recent = priorTurns.takeLast(6)
            if (recent.isNotEmpty()) {
                append("\nEarlier in this chat:\n")
                for (t in recent) {
                    append("User: ").append(t.userText.take(600)).append('\n')
                    append("Orbit: ").append((t.result ?: t.outcome).take(600)).append('\n')
                }
            }
            append("\nRequest:\n").append(goal)
        }
    }
}
