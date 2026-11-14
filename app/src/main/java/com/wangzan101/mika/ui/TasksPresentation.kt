package com.wangzan101.mika.ui

import com.wangzan101.mika.core.agent.RunProgress
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.personal.PersonalTask

/** Presentation groups only; task execution and confirmation remain owned by the runtime. */
internal enum class TaskSection { Review, NeedsYou, Working, Waiting, Upcoming, Resolved }

internal fun taskSection(state: TaskState): TaskSection? = when (state) {
    TaskState.Idle -> null
    is TaskState.WaitingConfirmation -> TaskSection.Review
    is TaskState.WaitingInput, is TaskState.WaitingTakeover -> TaskSection.NeedsYou
    is TaskState.Held -> when {
        state.reason.queued -> TaskSection.Upcoming
        state.reason.needsYou || state.reason.resumable -> TaskSection.NeedsYou
        else -> TaskSection.Waiting
    }
    is TaskState.Running -> TaskSection.Working
    is TaskState.Terminal -> TaskSection.Resolved
}

internal fun taskSection(task: PersonalTask, live: TaskState?): TaskSection =
    live?.let(::taskSection) ?: when {
        task.terminal -> TaskSection.Resolved
        task.status == "needs_you" -> TaskSection.NeedsYou
        task.status == "queued" -> TaskSection.Upcoming
        task.status == "running" || task.status == "finishing" -> TaskSection.Working
        else -> TaskSection.Waiting
    }

/** Streamed answer text is rendered in chat, not in task rows. */
internal fun taskDisplayState(state: TaskState): TaskState =
    if (state is TaskState.Running && state.preview != null) state.copy(preview = null) else state

/** Only the runtime's explicit done/total pair represents completed work. */
internal fun taskProgress(state: TaskState): Float? {
    val progress = (state as? TaskState.Running)?.progress as? RunProgress.Task ?: return null
    val done = progress.done ?: return null
    val total = progress.total ?: return null
    return if (total > 0 && done in 0..total) done.toFloat() / total else null
}
