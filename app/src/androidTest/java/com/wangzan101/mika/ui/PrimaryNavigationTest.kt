package com.wangzan101.mika.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wangzan101.mika.ui.theme.MikaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrimaryNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun primaryTabsExposeAndUpdateTheirSelectedState() {
        compose.setContent {
            var current by remember { mutableStateOf(Route.Chat) }
            MikaTheme(dark = false, reduceMotion = true) {
                PrimaryNavigation(current) { current = it }
            }
        }
        compose.onNodeWithTag("primary-nav-Chat").assertIsSelected()
        compose.onNodeWithTag("primary-nav-Tasks").performClick().assertIsSelected()
        compose.onNodeWithTag("primary-nav-Make").performClick().assertIsSelected()
        compose.onNodeWithTag("primary-nav-Chat").performClick().assertIsSelected()
    }
}
