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
        compose.onNodeWithTag("confirmation-approve").performClick().assertIsNotEnabled()
        compose.onNodeWithTag("confirmation-decline").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, approvals)
            assertEquals(0, declines)
        }
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
