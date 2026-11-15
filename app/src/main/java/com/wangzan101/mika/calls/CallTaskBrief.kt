package com.wangzan101.mika.calls

import kotlinx.serialization.Serializable
import java.util.Collections

/** The owner's objective for a single outgoing call. It does not record a business outcome. */
@Serializable
data class CallTaskBrief(
    val goal: String,
    val questions: List<String> = emptyList(),
    val context: String = "",
    val constraints: String = "",
) {
    /** Validate before dialing and detach the pending call from any mutable form inputs. */
    fun validatedSnapshot(): CallTaskBrief {
        val normalizedGoal = goal.trim()
        val normalizedContext = context.trim()
        val normalizedConstraints = constraints.trim()
        require(normalizedGoal.length in 1..MAX_GOAL_CHARS) { "The goal for this call must be 1–400 characters" }
        require(questions.size <= MAX_QUESTIONS) { "At most 8 must-ask items can be added for this call" }
        val normalizedQuestions = questions.map(String::trim)
        require(normalizedQuestions.all { it.length in 1..MAX_QUESTION_CHARS }) { "Each must-ask item must be 1–200 characters" }
        require(normalizedContext.length <= MAX_CONTEXT_CHARS) { "Background for this call can be at most 1000 characters" }
        require(normalizedConstraints.length <= MAX_CONSTRAINT_CHARS) { "Limits for this call can be at most 600 characters" }
        return CallTaskBrief(normalizedGoal, Collections.unmodifiableList(normalizedQuestions),
            normalizedContext, normalizedConstraints)
    }

    override fun toString(): String = "CallTaskBrief(questions=${questions.size})"

    companion object {
        const val MAX_GOAL_CHARS = 400
        const val MAX_QUESTIONS = 8
        const val MAX_QUESTION_CHARS = 200
        const val MAX_CONTEXT_CHARS = 1_000
        const val MAX_CONSTRAINT_CHARS = 600
    }
}
