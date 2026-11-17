package com.wangzan101.mika.ui

import com.wangzan101.mika.R
import com.wangzan101.mika.core.shell.BuiltinShellFailure
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.i18n.ChineseTexts
import com.wangzan101.mika.i18n.tr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The card a task shows while it waits for phone control to come back follows the helper's live state. */
class PhoneDownCopyTest {
    @get:Rule val texts = ChineseTexts()

    @Test
    fun wifiOffSaysSoInsteadOfOnlyDisconnected() {
        val card = PhoneDownCopy.card(BuiltinShellStatus.Failed(BuiltinShellFailure.NoWifi))
        assertEquals(tr(R.string.phone_down_title), card.title)
        assertEquals(tr(R.string.phone_down_no_wifi), card.body)
        assertFalse(card.back)
        assertFalse(card.connecting)
    }

    @Test
    fun wirelessDebuggingOffAndOtherFailuresOfferReconnect() {
        val off = PhoneDownCopy.card(BuiltinShellStatus.WirelessDebuggingOff)
        assertEquals(tr(R.string.phone_down_wireless_off), off.body)
        assertFalse(off.back)
        val failed = PhoneDownCopy.card(BuiltinShellStatus.Failed(BuiltinShellFailure.ConnectFailed))
        assertEquals(PhoneSetupCopy.failure(BuiltinShellFailure.ConnectFailed).title, failed.body)
        assertFalse(failed.back)
    }

    @Test
    fun reconnectingWaitsAndBackOffersContinue() {
        val connecting = PhoneDownCopy.card(BuiltinShellStatus.Connecting)
        assertTrue(connecting.connecting)
        assertFalse(connecting.back)
        val back = PhoneDownCopy.card(BuiltinShellStatus.Running)
        assertTrue(back.back)
        assertEquals(tr(R.string.phone_down_back_title), back.title)
    }
}
