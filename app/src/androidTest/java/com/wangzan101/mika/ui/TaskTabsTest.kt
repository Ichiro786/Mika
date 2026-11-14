package com.wangzan101.mika.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
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
class TaskTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothTabsAreVisibleAndSelectable() {
        compose.setContent {
            var selected by remember { mutableIntStateOf(0) }
            MikaTheme(dark = true, reduceMotion = true) {
                TaskTabs(selected) { selected = it }
            }
        }
        compose.onNodeWithTag("tasks-tab-0").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("tasks-tab-1").assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithTag("tasks-tab-0").performClick().assertIsSelected()
    }
}
