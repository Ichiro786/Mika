package com.wangzan101.mika.ui.plan

import androidx.compose.runtime.Immutable
import com.wangzan101.mika.ui.ChatCopy
import com.wangzan101.mika.ui.Exchange
import com.wangzan101.mika.ui.ExchangeStatus
import com.wangzan101.mika.ui.StepAction
import com.wangzan101.mika.ui.StepState
import com.wangzan101.mika.ui.isActive
import com.wangzan101.mika.ui.liveLine
import com.wangzan101.mika.core.record.RunEvent

/** How one step of a task stands. */
enum class PlanStepStatus { Pending, Running, Waiting, NeedsApproval, Done, Failed, Stopped }

/** Where the task as a whole is. */
enum class PlanPhase { Running, Waiting, NeedsApproval, Done, Failed, Stopped }

/**
 * One row of the live plan. [id] is the row's position and never changes once the row exists, so lists can
 * key on it and only genuinely new rows animate in. [note] is the one-line reason under a failed step.
 */
@Immutable
data class PlanStep(
    val id: Int,
    val text: String,
    val status: PlanStepStatus,
    val note: String = "",
    val actions: List<StepAction> = emptyList(),
    /** Where the step ran ("Web", "App"), shown as a small tag; null when not known. */
    val tag: String? = null,
)

/**
 * Everything the live plan card shows, in one shape for every surface: chat, the floating card, the capsule
 * pill and the mini view. Built only from what the task actually did ([Exchange.steps]) plus what it is doing
 * now; there is no invented plan. [total] is null until the agent publishes a plan, so the header reads
 * "Step 3" rather than claiming a "3 of 5" it does not know.
 */
@Immutable
data class LivePlan(
    val title: String,
    val steps: List<PlanStep>,
    val phase: PlanPhase,
    val startedAt: Long = 0L,
    val elapsedMs: Long? = null,
    val total: Int? = null,
    val runId: String? = null,
) {
    val active: Boolean get() = phase == PlanPhase.Running || phase == PlanPhase.Waiting || phase == PlanPhase.NeedsApproval

    /** The row the user's eye should be on: the running or waiting one, else the last. */
    val currentIndex: Int get() = steps.indexOfLast {
        it.status == PlanStepStatus.Running || it.status == PlanStepStatus.Waiting || it.status == PlanStepStatus.NeedsApproval
    }.let { if (it < 0) steps.lastIndex else it }

    val current: PlanStep? get() = steps.getOrNull(currentIndex)

    /** 1-based number of the current step; 0 when there are no steps yet. */
    val stepNumber: Int get() = currentIndex + 1

    /** Share of the plan finished, 0..1, when the total is known; null otherwise (the ring spins instead). */
    val progress: Float? get() = total?.takeIf { it > 0 }?.let { t ->
        (steps.count { it.status == PlanStepStatus.Done }.toFloat() / t).coerceIn(0f, 1f)
    }
}

object LivePlans {
    /** The plan for [ex], with what it is doing now from [liveLine]. */
    fun from(ex: Exchange): LivePlan = from(
        ex,
        when {
            !ex.status.isActive() -> null
            ex.status is ExchangeStatus.Paused -> (ex.status as ExchangeStatus.Paused).why.takeIf(String::isNotBlank) ?: liveLine(ex)
            else -> liveLine(ex)
        },
    )

    /**
     * Builds the plan. Each row the task really did becomes a step: failed ones keep their one-line reason,
     * the last one follows the task (running, waiting on you, needs approval, stopped). While running, the
     * current action is added as the last row when the step list has not caught up with it yet, so the card
     * always shows what is happening now even before any step is recorded.
     */
    fun from(ex: Exchange, now: String?): LivePlan {
        val phase = phase(ex.status)
        planned(ex, phase)?.let { return it }
        val approval = (ex.status as? ExchangeStatus.NeedsConfirmation)?.title
        val out = ex.steps.mapIndexed { i, row ->
            val failed = row.subtitle.isNotBlank()
            val status = when (row.state) {
                StepState.Ok -> if (failed) PlanStepStatus.Failed else PlanStepStatus.Done
                StepState.Run -> PlanStepStatus.Running
                StepState.Wait -> if (phase == PlanPhase.NeedsApproval) PlanStepStatus.NeedsApproval else PlanStepStatus.Waiting
                StepState.Dim -> if (failed || phase == PlanPhase.Failed) PlanStepStatus.Failed else PlanStepStatus.Stopped
            }
            PlanStep(i, row.text, status, note = row.subtitle, actions = ex.stepActions[row.step].orEmpty())
        }.toMutableList()
        if (phase == PlanPhase.Running || phase == PlanPhase.Waiting || phase == PlanPhase.NeedsApproval) {
            val line = (approval ?: now)?.trim().orEmpty()
            val tail = when (phase) {
                PlanPhase.NeedsApproval -> PlanStepStatus.NeedsApproval
                PlanPhase.Waiting -> PlanStepStatus.Waiting
                else -> PlanStepStatus.Running
            }
            val last = out.lastOrNull()
            if (line.isNotEmpty() && last?.text != line) {
                // The recorded last step is finished once something newer is happening.
                if (last != null && last.status in LiveStates) out[out.lastIndex] = last.copy(status = PlanStepStatus.Done)
                out += PlanStep(out.size, line, tail)
            } else if (last != null && last.status in LiveStates) {
                out[out.lastIndex] = last.copy(status = tail)
            }
        }
        return LivePlan(
            title = ex.goal.trim(),
            steps = out,
            phase = phase,
            startedAt = ex.startedAt,
            elapsedMs = ex.elapsedMs,
            runId = ex.runId,
        )
    }

    /**
     * The plan the agent published with update_plan, newest first: its steps with the ones before the current
     * step done, the current one following the task, the rest still to come. Null when it published none.
     */
    fun planned(ex: Exchange, phase: PlanPhase = phase(ex.status)): LivePlan? {
        val rec = ex.activities.lastOrNull { it.kind == RunEvent.PLAN_ACTIVITY } ?: return null
        val steps = rec.title?.lines()?.map(String::trim)?.filter(String::isNotBlank)?.takeIf { it.size >= 2 } ?: return null
        val current = ((rec.target?.toIntOrNull() ?: 1) - 1).coerceIn(0, steps.lastIndex)
        val here = when (phase) {
            PlanPhase.Running -> PlanStepStatus.Running
            PlanPhase.Waiting -> PlanStepStatus.Waiting
            PlanPhase.NeedsApproval -> PlanStepStatus.NeedsApproval
            PlanPhase.Done -> PlanStepStatus.Done
            PlanPhase.Failed -> PlanStepStatus.Failed
            PlanPhase.Stopped -> PlanStepStatus.Stopped
        }
        val failedNote = (ex.status as? ExchangeStatus.Failed)?.reason?.lineSequence()?.firstOrNull().orEmpty()
        return LivePlan(
            title = ex.goal.trim(),
            steps = steps.mapIndexed { i, text ->
                val status = when {
                    phase == PlanPhase.Done -> PlanStepStatus.Done
                    i < current -> PlanStepStatus.Done
                    i == current -> here
                    else -> PlanStepStatus.Pending
                }
                PlanStep(i, text, status, note = if (status == PlanStepStatus.Failed) failedNote else "")
            },
            phase = phase,
            startedAt = ex.startedAt,
            elapsedMs = ex.elapsedMs,
            total = steps.size,
            runId = ex.runId,
        )
    }

    fun phase(status: ExchangeStatus): PlanPhase = when {
        status is ExchangeStatus.NeedsConfirmation -> PlanPhase.NeedsApproval
        status is ExchangeStatus.Done -> PlanPhase.Done
        status is ExchangeStatus.Failed -> PlanPhase.Failed
        status is ExchangeStatus.Cancelled -> PlanPhase.Stopped
        ChatCopy.paused(status) || status is ExchangeStatus.Queued -> PlanPhase.Waiting
        else -> PlanPhase.Running
    }

    private val LiveStates = setOf(PlanStepStatus.Running, PlanStepStatus.Waiting, PlanStepStatus.NeedsApproval)
}
