package com.wangzan101.mika.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.wangzan101.mika.ui.identity.PresenceState
import kotlinx.coroutines.delay

/** How long the success halo stays after a task finishes before the presence settles back to idle. */
internal const val SUCCESS_HOLD_MS = 3_000L

/**
 * What the presence shows for the latest exchange, plus when a success should settle.
 * [successUntil] is set only for [PresenceState.Success]; null there means the finish time is unknown.
 */
internal data class PresenceSignal(val state: PresenceState, val successUntil: Long? = null)

/** Presentation only: never infer activity from an old conversation. */
internal fun presenceSignal(exchange: Exchange?, listening: Boolean): PresenceSignal {
    if (listening) return PresenceSignal(PresenceState.Listening)
    if (exchange == null || !exchange.live) return PresenceSignal(PresenceState.Idle)
    val state = when (val status = exchange.status) {
        is ExchangeStatus.NeedsConfirmation, is ExchangeStatus.NeedsInput,
        is ExchangeStatus.NeedsTakeover, ExchangeStatus.WaitingUnlock -> PresenceState.NeedsUser
        is ExchangeStatus.Paused -> if (status.resumable) PresenceState.NeedsUser else PresenceState.Idle
        is ExchangeStatus.Done -> PresenceState.Success
        is ExchangeStatus.Failed -> PresenceState.Error
        // A tool call in flight is work; a model request with nothing to show yet is thinking;
        // narration or progress without a tool is still the agent moving the task along.
        ExchangeStatus.Working -> when {
            exchange.toolRunning -> PresenceState.Working
            exchange.waitingForModel -> PresenceState.Thinking
            else -> PresenceState.Working
        }
        is ExchangeStatus.Queued, is ExchangeStatus.Cancelled -> PresenceState.Idle
    }
    val until = if (state == PresenceState.Success && exchange.startedAt > 0L && exchange.elapsedMs != null) {
        exchange.startedAt + exchange.elapsedMs + SUCCESS_HOLD_MS
    } else null
    return PresenceSignal(state, until)
}

/** The state alone, before any time-based settling. */
internal fun presenceFor(exchange: Exchange?, listening: Boolean): PresenceState = presenceSignal(exchange, listening).state

/** Shows success briefly after a task finishes, then settles to idle; every other state passes through. */
@Composable
internal fun settledPresence(signal: PresenceSignal): PresenceState {
    if (signal.state != PresenceState.Success) return signal.state
    val firstSeen = remember(signal) { System.currentTimeMillis() }
    val until = signal.successUntil ?: (firstSeen + SUCCESS_HOLD_MS)
    var now by remember(signal) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(signal, until) {
        val left = until - System.currentTimeMillis()
        if (left > 0) delay(left)
        now = System.currentTimeMillis()
    }
    return if (now >= until) PresenceState.Idle else PresenceState.Success
}
