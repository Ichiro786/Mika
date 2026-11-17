package com.wangzan101.mika.ui

import com.wangzan101.mika.core.agent.TaskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A streamed token must not rebuild the step rows; anything else still does. */
class ChatReduceTest {
    private val start = Exchange("问", emptyList(), ExchangeStatus.Working, live = true)

    @Test
    fun tokensKeepTheRowsBuiltLastTime() {
        val first = ChatViewModel.reduce(start, TaskState.Running(1, "", preview = "He"))
        val second = ChatViewModel.reduce(first, TaskState.Running(1, "", preview = "Hello"))
        assertEquals("Hello", second.draft)
        // The same list objects, not equal copies: nothing was rebuilt.
        assertSame(first.steps, second.steps)
        assertSame(first.stepActions, second.stepActions)
        assertTrue(ChatViewModel.onlyDraftChanged(first, second, toolRunning = false))
    }

    @Test
    fun aRealChangeIsNotMistakenForAToken() {
        val streaming = ChatViewModel.reduce(start, TaskState.Running(1, "", preview = "He"))
        // A tool starts: not a token.
        val tool = ChatViewModel.reduce(streaming, TaskState.Running(2, "", tool = "phone_tap", preview = "He"))
        assertTrue(tool.toolRunning)
        assertFalse(ChatViewModel.onlyDraftChanged(streaming, tool, toolRunning = true))
        // The first text of a reply is not a "token only" change either: the rows have not been built from it yet.
        assertFalse(ChatViewModel.onlyDraftChanged(start, streaming, toolRunning = false))
        // The reply ends: the state is rebuilt as before.
        val done = ChatViewModel.reduce(streaming, TaskState.Succeeded("Hello"))
        assertTrue(done.status is ExchangeStatus.Done)
    }
}
