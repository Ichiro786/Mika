package com.wangzan101.mika.gemini

import com.wangzan101.mika.core.model.Capabilities
import com.wangzan101.mika.core.model.ChatModel
import com.wangzan101.mika.core.model.Message
import com.wangzan101.mika.core.model.ModelReply
import com.wangzan101.mika.core.model.ToolSpec

/** Stands in for Orbit's own model while Gemini runs the task; Orbit's loop never calls it. */
internal object GeminiPlanModel : ChatModel {
    override val capabilities = Capabilities(toolCalls = true, vision = true, contextTokens = 1_000_000, reasoning = true)
    override val name: String get() = "gemini-plan"
    override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply =
        throw IllegalStateException("Gemini plan tasks don't use Orbit's own model")
}
