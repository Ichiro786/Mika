package com.wangzan101.mika.ui

import com.wangzan101.mika.ui.identity.PresenceState

/** Presentation only: never infer activity from an old conversation or an ambiguous running state. */
internal fun presenceFor(exchange: Exchange?, listening: Boolean): PresenceState {
    if (listening) return PresenceState.Listening
    if (exchange == null || !exchange.live) return PresenceState.Idle
    return when (exchange.status) {
        is ExchangeStatus.NeedsConfirmation, is ExchangeStatus.NeedsInput,
        is ExchangeStatus.NeedsTakeover -> PresenceState.NeedsUser
        is ExchangeStatus.Done -> PresenceState.Success
        is ExchangeStatus.Failed -> PresenceState.Error
        ExchangeStatus.Working -> when {
            exchange.toolRunning -> PresenceState.Working
            exchange.waitingForModel && !exchange.draft.isNullOrBlank() -> PresenceState.Thinking
            else -> PresenceState.Idle
        }
        else -> PresenceState.Idle
    }
}
