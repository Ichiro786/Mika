package com.wangzan101.mika.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.wangzan101.mika.R
import com.wangzan101.mika.core.shell.BuiltinShellFailure
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.i18n.tr

/**
 * The phone control helper's live state and the existing "reconnect with the paired key" action, handed to the
 * chat so a task that stopped because the helper dropped can say why and offer Reconnect in place.
 */
@Immutable
class PhoneLink(val status: BuiltinShellStatus, val reconnect: () -> Unit)

/** Null where nothing provides it (previews, tests): the chat then shows the plain pause card. */
val LocalPhoneLink = compositionLocalOf<PhoneLink?> { null }

/**
 * What the chat says while a task waits for phone control to come back, by the helper's state.
 * Wireless debugging needs Wi-Fi, so with Wi-Fi off a dropped helper cannot be started again: the card says so
 * instead of only "disconnected".
 */
internal object PhoneDownCopy {
    data class Card(
        val title: String,
        val body: String,
        /** The helper is back: offer Continue. */
        val back: Boolean = false,
        /** A reconnect is already under way: the button is there but there is nothing to tap yet. */
        val connecting: Boolean = false,
    )

    fun card(status: BuiltinShellStatus): Card = when (status) {
        BuiltinShellStatus.Running -> Card(tr(R.string.phone_down_back_title), tr(R.string.phone_down_back_body), back = true)
        BuiltinShellStatus.Connecting -> Card(tr(R.string.banner_connecting), tr(R.string.banner_connecting_detail), connecting = true)
        BuiltinShellStatus.WirelessDebuggingOff -> Card(tr(R.string.phone_down_title), tr(R.string.phone_down_wireless_off))
        BuiltinShellStatus.NotPaired -> Card(tr(R.string.phone_down_title), tr(R.string.banner_not_paired_detail))
        is BuiltinShellStatus.Failed ->
            if (status.reason == BuiltinShellFailure.NoWifi) Card(tr(R.string.phone_down_title), tr(R.string.phone_down_no_wifi))
            else Card(tr(R.string.phone_down_title), PhoneSetupCopy.failure(status.reason).title)
    }
}
