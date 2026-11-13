package com.wangzan101.mika.ui.identity

import androidx.compose.ui.graphics.Color
import com.wangzan101.mika.settings.OrbitIdentityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OrbitIdentityPaletteTest {
    @Test fun originalKeepsCoralAndAdaptsItsWave() {
        val light = orbitIdentityPalette(OrbitIdentityMode.Original, dark = false)
        val dark = orbitIdentityPalette(OrbitIdentityMode.Original, dark = true)
        assertEquals(Color(0xFFE97D60), light.orb)
        assertEquals(light.orb, dark.orb)
        assertNotEquals(light.wave, dark.wave)
    }

    @Test fun monochromeAdaptsWithoutCoral() {
        val light = orbitIdentityPalette(OrbitIdentityMode.Monochrome, dark = false)
        val dark = orbitIdentityPalette(OrbitIdentityMode.Monochrome, dark = true)
        assertEquals(Color(0xFF22201F), light.wave)
        assertEquals(Color(0xFFF8F5F1), dark.wave)
        assertNotEquals(Color(0xFFE97D60), light.orb)
        assertNotEquals(Color(0xFFE97D60), dark.orb)
    }
}
