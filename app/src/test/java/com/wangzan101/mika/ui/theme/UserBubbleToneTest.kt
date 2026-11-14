package com.wangzan101.mika.ui.theme

import androidx.compose.ui.graphics.Color
import com.wangzan101.mika.settings.ThemeAccent
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Your own bubbles carry the accent and their text always reads at 4.5:1 or better. */
class UserBubbleToneTest {
    private val accents = ThemeAccent.entries.flatMap { listOf(accentTone(it, true).first, accentTone(it, false).first) } +
        listOf(Color(0xFFFFEB3B), Color(0xFF00E5FF), Color(0xFFFF1744), Color.White, Color.Black)

    @Test fun textReadsOnEveryAccent() {
        for (dark in listOf(true, false)) for (accent in accents) {
            val tone = userBubbleTone(accent, dark)
            assertTrue("$accent dark=$dark", contrast(tone.bg, tone.fg) >= 4.5)
        }
    }

    @Test fun bubbleIsTintedNotPlain() {
        val blue = accentTone(ThemeAccent.Blue, true).first
        assertNotEquals(Color(0xFF0E0E10), userBubbleTone(blue, dark = true).bg)
        assertNotEquals(Color.White, userBubbleTone(blue, dark = false).bg)
    }
}
