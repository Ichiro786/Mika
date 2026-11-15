package com.wangzan101.mika.ui.theme

import androidx.compose.ui.graphics.Color
import com.wangzan101.mika.settings.ThemeAccent
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorialPaletteTest {
    @Test fun warmAndAmoledBackgroundsSurviveEveryAccentPreference() {
        for (accent in ThemeAccent.entries) {
            assertEquals(Color(0xFFFAF9F7), palette(false, accent).bg)
            assertEquals(Color.Black, palette(true, accent).bg)
        }
    }
}
