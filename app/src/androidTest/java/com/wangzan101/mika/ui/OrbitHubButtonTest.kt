package com.wangzan101.mika.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wangzan101.mika.R
import com.wangzan101.mika.settings.OrbitIdentityMode
import com.wangzan101.mika.settings.OrbitPresenceSize
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

    @Test fun headerSizeUpdatesAndKeepsMinimumTouchTarget() {
        val size = mutableStateOf(OrbitPresenceSize(headerDp = 44))
        compose.setContent {
            MikaTheme(dark = true, reduceMotion = true, presence = size.value) {
                OrbitHubButton(PresenceState.Idle, onClick = {})
            }
        }
        compose.onNodeWithTag("orbit-hub-open").assertWidthIsEqualTo(48.dp)
        compose.runOnIdle { size.value = size.value.copy(headerDp = 76) }
        compose.onNodeWithTag("orbit-hub-open").assertWidthIsEqualTo(76.dp)
    }

    @Test fun hubSectionsHaveAccessibleLabelsWithoutVisibleTabText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val labels = listOf(R.string.orbit_hub_today, R.string.drawer_browser, R.string.memory_and_skills,
            R.string.file_library_title, R.string.orbit_hub_personalization).map { context.getString(it) }
        compose.setContent {
            MikaTheme(dark = true, reduceMotion = true) { OrbitHubTabs(0) {} }
        }
        labels.forEachIndexed { index, label ->
            compose.onNodeWithTag("orbit-hub-section-$index")
                .assertIsDisplayed().assertHasClickAction().assert(hasContentDescription(label))
            compose.onNodeWithText(label, useUnmergedTree = true).assertDoesNotExist()
        }
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
