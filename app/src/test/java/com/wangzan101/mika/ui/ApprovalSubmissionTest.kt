package com.wangzan101.mika.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalSubmissionTest {
    @Test fun oneChoiceIsSentWhileTheSameRequestIsPending() {
        val submission = ApprovalSubmission()
        val calls = mutableListOf<String>()
        submission.send { calls += "approve" }
        submission.send { calls += "decline" }
        assertEquals(listOf("approve"), calls)
        assertTrue(submission.pending)
        assertFalse(submission.failed)
    }

    @Test fun aSynchronousFailureAllowsTheUserToRetry() {
        val submission = ApprovalSubmission()
        submission.send { error("not sent") }
        assertFalse(submission.pending)
        assertTrue(submission.failed)
        var sent = false
        submission.send { sent = true }
        assertTrue(sent)
        assertTrue(submission.pending)
        assertFalse(submission.failed)
    }
}
