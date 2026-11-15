package com.wangzan101.mika.ui.components

import com.wangzan101.mika.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaSpace

/**
 * 子页面的骨架:页面底 + 带返回的顶栏 + 一列可滚动的内容(左右留 [MikaSpace.Page],避开导航栏)。
 * 内容里直接放 [GroupLabel] / [GroupCard] / [FooterNote]。
 */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    viewportModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PageBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title, MikaIcons.Back, onBack, stringResource(R.string.back))
            Column(
                Modifier.weight(1f).fillMaxWidth().then(viewportModifier)
                    .scrollEdgeFade(scrollState)
                    .verticalScroll(scrollState)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = MikaSpace.Page),
            ) {
                Spacer(Modifier.height(MikaSpace.S4))
                content()
                Spacer(Modifier.height(MikaSpace.S24))
            }
        }
    }
}

/**
 * Content scrolled up under the top bar dissolves over a short band instead of being cut on a hard line.
 * Works over any page background (plain or skinned) because it masks the content itself.
 */
fun Modifier.scrollEdgeFade(state: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (state.value > 0) {
                val band = 18.dp.toPx()
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = band),
                    size = Size(size.width, band),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
