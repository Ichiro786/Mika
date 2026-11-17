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

    @Test fun streamingTextDoesNotChangeTaskPresentation() {
        val first = TaskState.Running(1, "working", preview = "first")
        val next = first.copy(preview = "first and more")
        assertEquals(taskDisplayState(first), taskDisplayState(next))
        assertEquals(TaskSection.Working, taskSection(taskDisplayState(next)))
    }

    @Test fun fractionOnlyUsesReportedCompletedWork() {
        fun state(progress: RunProgress) = TaskState.Running(1, "", progress = progress)
        assertEquals(0.25f, taskProgress(state(RunProgress.Task("", 1, 4, null)))!!, 0f)
        assertNull(taskProgress(state(RunProgress.Task("", null, 4, null))))
        assertNull(taskProgress(state(RunProgress.Task("", 5, 4, null))))
        assertNull(taskProgress(state(RunProgress.Replay(1, 4))))
        assertNull(taskProgress(TaskState.Idle))
    }

    @Test
    fun fourGroupsFoldTheRuntimeStates() {
        assertEquals(TaskGroup.Running, taskGroup(TaskSection.Working))
        assertEquals(TaskGroup.Running, taskGroup(TaskSection.Waiting))
        assertEquals(TaskGroup.NeedsYou, taskGroup(TaskSection.Review))
        assertEquals(TaskGroup.NeedsYou, taskGroup(TaskSection.NeedsYou))
        assertEquals(TaskGroup.UpNext, taskGroup(TaskSection.Upcoming))
        assertEquals(TaskGroup.Done, taskGroup(TaskSection.Resolved))
        // Marked done by the user: filed under Done whatever the runtime still says.
        assertEquals(TaskGroup.Done, taskGroup(TaskSection.Working, markedDone = true))
        assertEquals(TaskGroup.Done, taskGroup(TaskSection.NeedsYou, markedDone = true))
    }

    @Test
    fun aMarkMadeWhileRunningStillFindsTheTaskInHistory() {
        val live = taskMarkIds(personalId = null, liveKey = "k1", recordId = "rec1", storedId = null)
        val stored = taskMarkIds(personalId = null, liveKey = null, recordId = "rec1", storedId = "run9")
        assertEquals(listOf("l:k1", "r:rec1"), live)
        assertEquals(listOf("r:rec1", "r:run9"), stored)
        assertEquals(true, taskMarked(stored, live.toSet()))
        assertEquals(false, taskMarked(taskMarkIds(null, null, "rec2", "run10"), live.toSet()))
        assertEquals(listOf("p:t1"), taskMarkIds(personalId = "t1", liveKey = null, recordId = null, storedId = null))
    }

    @Test
    fun marksKeepTheNewestWithoutRepeats() {
        assertEquals(listOf("a", "c", "b"), TaskMarks.kept(listOf("a", "b", "c"), listOf("b")))
        val many = List(TaskMarks.MAX + 5) { "id$it" }
        val kept = TaskMarks.kept(many, listOf("new"))
        assertEquals(TaskMarks.MAX, kept.size)
        assertEquals("new", kept.last())
        assertEquals("id6", kept.first())
    }
}
