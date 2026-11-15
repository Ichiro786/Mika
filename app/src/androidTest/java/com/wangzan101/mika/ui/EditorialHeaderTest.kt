package com.wangzan101.mika.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wangzan101.mika.ui.components.BarIconButton
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorialHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun avatarIsCenteredWithUnevenActions() {
        compose.setContent {
            MikaTheme(dark = true, reduceMotion = true) {
                Box(Modifier.fillMaxWidth().testTag("header-bounds")) {
                    TopBar("", left = MikaIcons.Menu, titleIcon = {
                        Box(Modifier.size(52.dp).testTag("header-avatar"))
                    }, centerTitle = true, right = {
                        BarIconButton(MikaIcons.Paperclip, "Files", {})
                        BarIconButton(MikaIcons.New, "New", {})
                    })
                }
            }
        }
        val header = compose.onNodeWithTag("header-bounds").fetchSemanticsNode().boundsInRoot
        val avatar = compose.onNodeWithTag("header-avatar").fetchSemanticsNode().boundsInRoot
        assertEquals(header.center.x, avatar.center.x, 1f)
    }
}
