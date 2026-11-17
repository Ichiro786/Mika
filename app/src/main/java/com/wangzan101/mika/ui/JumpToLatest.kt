package com.wangzan101.mika.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.components.FrostedDisc
import com.wangzan101.mika.ui.components.GlassBackdrop
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaTheme

/**
 * Jump to latest: a small round frosted arrow that floats just above the message bar while the user is scrolled
 * up more than about a screen. [unread] replies that arrived meanwhile show as a count on it. The caller places
 * it (above the bar, so it also rides above the keyboard) and scrolls; a tap gives a light tick.
 * The look is a 40 dp disc inside a 48 dp touch target.
 */
@Composable
internal fun JumpToLatestButton(
    visible: Boolean,
    unread: Int,
    reduceMotion: Boolean,
    backdrop: GlassBackdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MikaTheme.colors
    val haptic = LocalHapticFeedback.current
    val label = if (unread > 0) stringResource(R.string.chat_jump_latest_new, unread) else stringResource(R.string.chat_jump_latest)
    AnimatedVisibility(
        visible, modifier,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 380f), initialScale = 0.6f),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.8f),
    ) {
        Box(
            Modifier.size(MikaSize.TouchTarget)
                .clickable(remember { MutableInteractionSource() }, ripple(bounded = false, radius = 24.dp), role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
                .semantics { contentDescription = label }
                .testTag("jump-to-latest"),
            contentAlignment = Alignment.Center,
        ) {
            FrostedDisc(Modifier.size(MikaSize.IconButton), backdrop) {
                Icon(MikaIcons.ArrowDown, null, Modifier.align(Alignment.Center).size(20.dp), tint = c.ink)
            }
            if (unread > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).defaultMinSize(18.dp, 18.dp).background(c.accent, CircleShape)
                        .padding(horizontal = 5.dp).testTag("jump-to-latest-count").clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) {
                    Text(unreadBadge(unread), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = c.onAccent, maxLines = 1)
                }
            }
        }
    }
}
