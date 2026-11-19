package com.wangzan101.mika.ui.plan

import com.wangzan101.mika.ui.Exchange
import com.wangzan101.mika.ui.ExchangeStatus
import com.wangzan101.mika.ui.StepRow
import com.wangzan101.mika.ui.StepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlansTest {
    private fun row(i: Int, text: String, state: StepState = StepState.Ok, failed: String = "") =
        StepRow(i, text, failed, state)

    private fun ex(status: ExchangeStatus, vararg rows: StepRow) =
        Exchange("Book a table", rows.toList(), status, live = true, startedAt = 1_000L)

    @Test fun runningLastStepIsTheCurrentOne() {
        val plan = LivePlans.from(ex(ExchangeStatus.Working, row(1, "Open Maps"), row(2, "Search", StepState.Run)), now = "Search")
        assertEquals(listOf(PlanStepStatus.Done, PlanStepStatus.Running), plan.steps.map { it.status })
        assertEquals(2, plan.stepNumber)
        assertTrue(plan.active)
        assertNull("no invented total", plan.total)
        assertNull(plan.progress)
    }

    @Test fun noStepsYetShowsTheCurrentAction() {
        val plan = LivePlans.from(ex(ExchangeStatus.Working), now = "Opening Maps")
        assertEquals(1, plan.steps.size)
        assertEquals("Opening Maps", plan.steps[0].text)
        assertEquals(PlanStepStatus.Running, plan.steps[0].status)
    }

    @Test fun newerActionFinishesThePreviousStepAndKeepsIds() {
        val plan = LivePlans.from(ex(ExchangeStatus.Working, row(1, "Open Maps", StepState.Run)), now = "Type the address")
        assertEquals(listOf(PlanStepStatus.Done, PlanStepStatus.Running), plan.steps.map { it.status })
        assertEquals(listOf(0, 1), plan.steps.map { it.id })
    }

    @Test fun failedStepKeepsItsReason() {
        val plan = LivePlans.from(ex(ExchangeStatus.Working, row(1, "Tap Send", failed = "Didn't work"), row(2, "Retry", StepState.Run)), "Retry")
        assertEquals(PlanStepStatus.Failed, plan.steps[0].status)
        assertEquals("Didn't work", plan.steps[0].note)
    }

    @Test fun approvalIsAmberWithTheActionAndNeverApproves() {
        val status = ExchangeStatus.NeedsConfirmation("Needs your OK: pay 12 USD", "body", key = 7)
        val plan = LivePlans.from(ex(status, row(1, "Fill the form", StepState.Wait)), now = "Waiting")
        assertEquals(PlanPhase.NeedsApproval, plan.phase)
        assertEquals(PlanStepStatus.Done, plan.steps[0].status)
        assertEquals(PlanStepStatus.NeedsApproval, plan.steps.last().status)
        assertEquals("Needs your OK: pay 12 USD", plan.steps.last().text)
    }

    @Test fun finishedTaskAddsNoLiveRow() {
        val plan = LivePlans.from(ex(ExchangeStatus.Done("ok", null), row(1, "Open"), row(2, "Book")), now = "ignored")
        assertEquals(2, plan.steps.size)
        assertTrue(plan.steps.all { it.status == PlanStepStatus.Done })
        assertFalse(plan.active)
    }

    @Test fun failedTaskMarksItsLastStep() {
        val plan = LivePlans.from(ex(ExchangeStatus.Failed("no network"), row(1, "Open"), row(2, "Book", StepState.Dim)), now = null)
        assertEquals(PlanPhase.Failed, plan.phase)
        assertEquals(PlanStepStatus.Failed, plan.steps.last().status)
    }

    @Test fun progressOnlyWhenTotalIsKnown() {
        val plan = LivePlans.from(ex(ExchangeStatus.Working, row(1, "a"), row(2, "b"), row(3, "c", StepState.Run)), now = "c")
            .copy(total = 4)
        assertEquals(0.5f, plan.progress!!, 0.001f)
    }
}
