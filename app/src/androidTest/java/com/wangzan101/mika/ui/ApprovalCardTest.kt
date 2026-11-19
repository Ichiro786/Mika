package com.wangzan101.mika.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wangzan101.mika.ui.theme.MikaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import com.wangzan101.mika.ui.components.HOLD_MS

@RunWith(AndroidJUnit4::class)
class ApprovalCardTest {
    @get:Rule val compose = createComposeRule()

    private fun show(onApprove: () -> Unit, onDecline: () -> Unit) {
        compose.setContent {
            MikaTheme(dark = false, reduceMotion = true) {
                val submission = remember { ApprovalSubmission() }
                ApprovalCard(ExchangeStatus.NeedsConfirmation("Review action", "Actual request details", 42),
                    submission, onApprove, onDecline)
            }
        }
    }

    @Test fun approvingShowsTheRealDetailsAndDisablesBothChoices() {
        var approvals = 0
        var declines = 0
        show({ approvals++ }, { declines++ })
        compose.onNodeWithText("Actual request details").assertIsDisplayed()
        // Holding past the full ring approves exactly once, then both choices lock while it's sent.
        compose.onNodeWithTag("confirmation-approve").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_MS + 200L)
        compose.onNodeWithTag("confirmation-approve").performTouchInput { up() }
        compose.onNodeWithTag("confirmation-approve").assertIsNotEnabled()
        compose.onNodeWithTag("confirmation-decline").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, approvals)
            assertEquals(0, declines)
        }
    }

    @Test fun aTapOrAnEarlyReleaseNeverApproves() {
        var approvals = 0
        show({ approvals++ }, {})
        compose.onNodeWithTag("confirmation-approve").performClick()
        compose.onNodeWithTag("confirmation-approve").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(HOLD_MS / 2L)
        compose.onNodeWithTag("confirmation-approve").performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, approvals) }
        compose.onNodeWithTag("confirmation-approve").assertIsEnabled()
    }

    /** TalkBack and switch access can't hold: their deliberate double tap is the button's click action. */
    @Test fun accessibilityClickApprovesOnce() {
        var approvals = 0
        show({ approvals++ }, {})
        compose.onNodeWithTag("confirmation-approve").performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals(1, approvals) }
    }

    @Test fun decliningDoesNotApproveTheRequest() {
        var approvals = 0
        var declines = 0
        show({ approvals++ }, { declines++ })
        compose.onNodeWithTag("confirmation-decline").performClick().assertIsNotEnabled()
        compose.onNodeWithTag("confirmation-approve").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(0, approvals)
            assertEquals(1, declines)
        }
    }
}
