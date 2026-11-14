package com.wangzan101.mika.ui

import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.ui.identity.PresenceState
import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceStateTest {
    private val live = Exchange("goal", emptyList(), ExchangeStatus.Working, live = true)

    @Test fun mapsOnlyActualLiveSignals() {
        assertEquals(PresenceState.Idle, presenceFor(null, listening = false))
        assertEquals(PresenceState.Listening, presenceFor(null, listening = true))
        assertEquals(PresenceState.Thinking, presenceFor(live.copy(waitingForModel = true, draft = "streamed"), false))
        assertEquals(PresenceState.Working, presenceFor(live.copy(toolRunning = true), false))
        assertEquals(PresenceState.Working, presenceFor(live.copy(toolRunning = true, waitingForModel = true, draft = "streamed"), false))
        assertEquals(PresenceState.Idle, presenceFor(live, false))
        assertEquals(PresenceState.Idle, presenceFor(live.copy(waitingForModel = true), false))
        assertEquals(PresenceState.Idle, presenceFor(live.copy(status = ExchangeStatus.Queued("wait")), false))
    }

    @Test fun attentionAndTerminalStatesDoNotAnimateOldHistory() {
        val confirmation = live.copy(status = ExchangeStatus.NeedsConfirmation("title", "body", 1))
        val done = live.copy(status = ExchangeStatus.Done("answer", null))
        val failed = live.copy(status = ExchangeStatus.Failed("error"))
        assertEquals(PresenceState.NeedsUser, presenceFor(confirmation, false))
        assertEquals(PresenceState.NeedsUser, presenceFor(live.copy(status = ExchangeStatus.NeedsInput("question", null)), false))
        assertEquals(PresenceState.NeedsUser, presenceFor(live.copy(status = ExchangeStatus.NeedsTakeover("login", "sign in")), false))
        assertEquals(PresenceState.Success, presenceFor(done, false))
        assertEquals(PresenceState.Error, presenceFor(failed, false))
        assertEquals(PresenceState.Idle, presenceFor(done.copy(live = false), false))
        assertEquals(PresenceState.Idle, presenceFor(failed.copy(live = false), false))
        assertEquals(PresenceState.Listening, presenceFor(confirmation, true))
    }

    @Test fun toolSignalFollowsTheCurrentRuntimeState() {
        val runningTool = ChatViewModel.reduce(live, TaskState.Running(1, "", tool = "read"))
        val waitingModel = ChatViewModel.reduce(runningTool, TaskState.Running(2, "", preview = "streamed"))
        assertEquals(true, runningTool.toolRunning)
        assertEquals(false, waitingModel.toolRunning)
        assertEquals(PresenceState.Thinking, presenceFor(waitingModel, false))
        val done = ChatViewModel.reduce(runningTool, TaskState.Succeeded("done"))
        assertEquals(false, done.toolRunning)
    }
}
