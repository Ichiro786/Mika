package com.wangzan101.mika.ui.onboarding

import android.content.Context
import com.wangzan101.mika.R
import com.wangzan101.mika.i18n.tr
import com.wangzan101.mika.settings.RecommendedTask

/** The first-run flow, in order. */
enum class FlowStep { Welcome, CanDo, Privacy, Model, Phone, Permissions, Personal, Done }

/**
 * Which steps the first-run flow has and how to move through it (pure, so it can be tested).
 * - Every step has a way back; Welcome's way back leaves the flow.
 * - [optional] steps show Skip. The model step is the only one that must be finished: Orbit cannot answer without one.
 * - The phone step is left out where phone control cannot be set up at all.
 */
object OnboardingFlow {
    fun steps(phoneAvailable: Boolean): List<FlowStep> = FlowStep.entries.filter { it != FlowStep.Phone || phoneAvailable }

    fun next(step: FlowStep, steps: List<FlowStep>): FlowStep? = steps.getOrNull(steps.indexOf(step) + 1)

    fun previous(step: FlowStep, steps: List<FlowStep>): FlowStep? = steps.getOrNull(steps.indexOf(step) - 1)

    fun optional(step: FlowStep): Boolean = step in setOf(FlowStep.CanDo, FlowStep.Phone, FlowStep.Permissions, FlowStep.Personal)

    /** One-based position for the progress bar and for TalkBack ("Step 3 of 8"). */
    fun position(step: FlowStep, steps: List<FlowStep>): Int = steps.indexOf(step).coerceAtLeast(0) + 1

    /**
     * Whether the flow shows when the app opens.
     * - Finished once: never again (it can still be replayed from Settings).
     * - Left half way (the app was closed or killed): it comes back, on the saved step.
     * - Never started: only for someone with no model yet. Anyone who already set one up, which is every existing
     *   user who updates, goes straight to the chat.
     */
    fun shouldStart(done: Boolean, resume: FlowStep?, hasMainModel: Boolean, modelSetupDone: Boolean): Boolean =
        !done && (resume != null || (!hasMainModel && !modelSetupDone))

    /** Where to pick up: the saved step if it still exists in this flow, otherwise the start. */
    fun resumeAt(saved: FlowStep?, steps: List<FlowStep>): FlowStep = saved?.takeIf { it in steps } ?: FlowStep.Welcome

    /**
     * The three starters the chat offers right after the flow. With phone control: message someone, summarize
     * chats, a daily brief. Without it: things that work in the browser and with files, and the same daily brief.
     */
    fun starters(phoneReady: Boolean): List<RecommendedTask> = if (phoneReady) listOf(
        RecommendedTask("ob-message", tr(R.string.ob_starter_message), tr(R.string.ob_starter_message_prompt)),
        RecommendedTask("ob-summary", tr(R.string.ob_starter_summary), tr(R.string.ob_starter_summary_prompt)),
        RecommendedTask("ob-brief", tr(R.string.ob_starter_brief), tr(R.string.ob_starter_brief_prompt)),
    ) else listOf(
        RecommendedTask("ob-research", tr(R.string.ob_starter_research), tr(R.string.ob_starter_research_prompt)),
        RecommendedTask("ob-file", tr(R.string.ob_starter_file), tr(R.string.ob_starter_file_prompt)),
        RecommendedTask("ob-brief", tr(R.string.ob_starter_brief), tr(R.string.ob_starter_brief_prompt)),
    )

    /** A name as typed, tidied for greetings: trimmed, one line, not absurdly long. Blank means no name. */
    fun cleanName(typed: String): String = typed.replace(Regex("\\s+"), " ").trim().take(NAME_MAX)

    const val NAME_MAX = 40

    /** The public source, linked from the privacy step. */
    const val SOURCE_URL = "https://github.com/Ichiro786/Orbit"
}

/**
 * What the first-run flow remembers on this phone: whether it was finished, the step it was left on (so a killed
 * app reopens there), the name for greetings, and whether the chat should still offer the three starters.
 * Lives in the same preferences file as the earlier onboarding flags.
 */
class OnboardingProgress(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun done(): Boolean = prefs.getBoolean(DONE, false)

    /** Finishing clears the saved step and turns the starters on until the first message is sent. */
    fun markDone() {
        prefs.edit().putBoolean(DONE, true).remove(STEP).putBoolean(STARTERS, true).apply()
    }

    fun step(): FlowStep? = prefs.getString(STEP, null)?.let { name -> FlowStep.entries.firstOrNull { it.name == name } }

    fun saveStep(step: FlowStep) {
        prefs.edit().putString(STEP, step.name).apply()
    }

    fun name(): String = prefs.getString(NAME, "").orEmpty()

    fun setName(name: String) {
        prefs.edit().putString(NAME, OnboardingFlow.cleanName(name)).apply()
    }

    fun startersPending(): Boolean = prefs.getBoolean(STARTERS, false)

    fun clearStarters() {
        if (startersPending()) prefs.edit().putBoolean(STARTERS, false).apply()
    }

    private companion object {
        const val PREFS = "onboarding"
        const val DONE = "flow_done"
        const val STEP = "flow_step"
        const val NAME = "user_name"
        const val STARTERS = "starters_pending"
    }
}
