package com.wangzan101.mika.ui

import com.wangzan101.mika.R
import com.wangzan101.mika.settings.OrbitIdentityMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** The opening screen follows the logo style chosen for the app. */
class OpeningScreenThemeTest {
    @Test
    fun originalKeepsTheManifestThemeAndMonochromeSwitches() {
        // 0 clears any override, so the manifest's coral mark shows.
        assertEquals(0, openingScreenTheme(OrbitIdentityMode.Original))
        assertEquals(R.style.Theme_Mika_Mono, openingScreenTheme(OrbitIdentityMode.Monochrome))
        // An unknown stored value reads as Original, the default.
        assertEquals(0, openingScreenTheme(OrbitIdentityMode.parse("nonsense")))
    }
}
