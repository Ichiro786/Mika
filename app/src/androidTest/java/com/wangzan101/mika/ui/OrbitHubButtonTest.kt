package com.wangzan101.mika.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wangzan101.mika.settings.OrbitIdentityMode
import com.wangzan101.mika.ui.identity.PresenceState
import com.wangzan101.mika.ui.theme.MikaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrbitHubButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun monochromePresenceOpensHubOnClick() {
        var opens = 0
        compose.setContent {
            MikaTheme(dark = true, reduceMotion = true, identity = OrbitIdentityMode.Monochrome) {
                OrbitHubButton(PresenceState.Working, onClick = { opens++ })
            }
        }
        compose.onNodeWithTag("orbit-hub-open").assertIsDisplayed().assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }

    @Test fun allFiveHubSectionsCanBeSelected() {
        compose.setContent {
            var selected by remember { mutableIntStateOf(0) }
            MikaTheme(dark = true, reduceMotion = true) {
                OrbitHubTabs(selected) { selected = it }
            }
        }
        repeat(5) { index ->
            compose.onNodeWithTag("orbit-hub-section-$index").assertIsDisplayed().performClick().assertIsSelected()
        }
    }
}
