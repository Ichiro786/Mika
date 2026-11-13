package com.wangzan101.mika.ui

import com.wangzan101.mika.core.agent.GateReason
import com.wangzan101.mika.core.agent.RunProgress
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.gate.ConfirmationRequest
import com.wangzan101.mika.core.personal.PersonalTask
import com.wangzan101.mika.core.risk.IrrevAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TasksPresentationTest {
    private val task = PersonalTask("id", "conversation", "Title", "Goal", "source", 1L)
    private val confirmation = TaskState.WaitingConfirmation(
        ConfirmationRequest(IrrevAction.send, "person", "content", null, "app"),
    )

    @Test fun sectionsFollowActualRuntimeState() {
        assertEquals(TaskSection.Review, taskSection(confirmation))
        assertEquals(TaskSection.NeedsYou, taskSection(TaskState.WaitingInput("question")))
        assertEquals(TaskSection.Upcoming, taskSection(TaskState.Held(GateReason.queued("waiting"))))
        assertEquals(TaskSection.Waiting, taskSection(TaskState.Held(GateReason("blocked"))))
        assertEquals(TaskSection.Working, taskSection(TaskState.Running(1, "working")))
        assertEquals(TaskSection.Resolved, taskSection(TaskState.Succeeded("done")))
        assertNull(taskSection(TaskState.Idle))
    }

    @Test fun personalTasksUseLiveStateAndDoNotInventReviewOrProgress() {
        assertEquals(TaskSection.Review, taskSection(task.copy(status = "needs_you"), confirmation))
        assertEquals(TaskSection.NeedsYou, taskSection(task.copy(status = "needs_you"), null))
        assertEquals(TaskSection.Resolved, taskSection(task.copy(status = "failed"), null))
        assertEquals(TaskSection.Waiting, taskSection(task.copy(status = "unknown"), null))
    }

    @Test fun fractionOnlyUsesReportedCompletedWork() {
        fun state(progress: RunProgress) = TaskState.Running(1, "", progress = progress)
        assertEquals(0.25f, taskProgress(state(RunProgress.Task("", 1, 4, null)))!!, 0f)
        assertNull(taskProgress(state(RunProgress.Task("", null, 4, null))))
        assertNull(taskProgress(state(RunProgress.Task("", 5, 4, null))))
        assertNull(taskProgress(state(RunProgress.Replay(1, 4))))
        assertNull(taskProgress(TaskState.Idle))
    }
}
