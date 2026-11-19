package com.wangzan101.mika.core.general

import com.wangzan101.mika.core.prompt.Prompts
import com.wangzan101.mika.core.tool.AgentTool
import com.wangzan101.mika.core.tool.ArgError
import com.wangzan101.mika.core.tool.Args
import com.wangzan101.mika.core.tool.Risk
import com.wangzan101.mika.core.tool.ToolContext
import com.wangzan101.mika.core.tool.ToolDef
import com.wangzan101.mika.core.tool.ToolExecution
import com.wangzan101.mika.core.tool.Verb
import com.wangzan101.mika.core.tool.ok
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The task's plan as the user sees it: a short list of steps and which one is under way. It changes nothing on
 * the phone or the web; it only lets the progress card show the whole plan up front ("Step 2 of 5") instead of
 * only the steps already taken. The model calls it at the start of a multi-step task and again as it moves on
 * or the plan changes; one-step tasks never need it.
 */
internal object UpdatePlan : AgentTool {
    const val NAME = "update_plan"
    const val MAX_STEPS = 12
    const val STEP_MAX_CHARS = 80

    override val verb = Verb.UpdatePlan
    override val batchable = false
    override val label = "更新计划"
    override val risk = Risk.Read
    override val logged = true
    override val def = ToolDef(
        NAME, Prompts.tool(NAME),
        mapOf(
            "steps" to buildJsonObject {
                put("type", "array"); put("description", "The whole plan, 2-$MAX_STEPS short steps in the user's language, each under $STEP_MAX_CHARS characters.")
                put("minItems", 2); put("maxItems", MAX_STEPS); put("items", buildJsonObject { put("type", "string") })
            },
            "current" to buildJsonObject {
                put("type", "integer"); put("description", "1-based number of the step being worked on now.")
                put("minimum", 1); put("maximum", MAX_STEPS)
            },
        ),
        listOf("steps", "current"),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val steps = a.strList("steps", 2, MAX_STEPS, STEP_MAX_CHARS)!!.map(String::trim)
        if (steps.any(String::isBlank)) throw ArgError("steps", "steps must not be blank")
        val current = a.int("current", 1, steps.size) ?: throw ArgError("current", "must be between 1 and ${steps.size}")
        return ok { put("planSteps", steps.size); put("current", current) }
    }

    /** How a plan is kept in the run record: steps one per line, the current step number as the activity's target. */
    fun encode(steps: List<String>): String = steps.joinToString("\n") { it.replace('\n', ' ') }
}
