package com.wangzan101.mika.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/**
 * 空状态:一个小图标、一句话、一句提示,加一个能立刻做的动作(没有合适的动作就不给 [actionLabel])。
 * 比 [EmptyNote] 多了出路,样式仍然克制:中性色、居中、不抢戏。
 */
@Composable
fun EmptyStateCard(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = MikaTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = c.ink3)
        Text(title, style = MikaType.Body, color = c.ink2, textAlign = TextAlign.Center)
        if (!hint.isNullOrEmpty()) Text(hint, style = MikaType.Hint, color = c.ink3, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            MikaButton(
                actionLabel, onAction, Modifier.padding(top = 8.dp),
                kind = ButtonKind.Tonal, size = ButtonSize.Small,
            )
        }
    }
}
