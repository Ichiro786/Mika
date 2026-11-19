package com.wangzan101.mika.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.theme.MikaMotion
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaSpace
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.ui.theme.motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.wangzan101.mika.ui.theme.MikaSize

/**
 * The button for consequential actions (a payment, anything that can't be undone): hold it for [HOLD_MS] while a
 * ring fills, feel a tick, and only then [onAllow] runs, once. Lifting early cancels and the ring springs back.
 *
 * The hold is measured in real time, frame by frame, not with an animation spec, so "Remove animations" never
 * turns it into a one-tap approval; it only stops the ring from springing back. TalkBack and switch access can't
 * hold, so they get the button's click action instead: a deliberate double tap on a control that says what it
 * does, which is the platform's own form of explicit consent. Nothing here decides anything: [onAllow] is the
 * caller's existing, keyed approve action.
 */
@Composable
fun HoldToAllowButton(
    text: String,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = MikaTheme.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val allow by rememberUpdatedState(onAllow)
    val active by rememberUpdatedState(enabled)
    val release = MikaTheme.motion.spatialFast<Float>()
    val holdHint = stringResource(R.string.hold_to_allow_hint)
    val holding = stringResource(R.string.hold_to_allow_holding)
    Box(
        modifier
            .heightIn(min = MikaSize.TouchTarget)
            .graphicsLayer { val s = 1f - (1f - MikaMotion.PRESS_SCALE) * progress.value.coerceAtMost(1f); scaleX = s; scaleY = s }
            .clip(MikaShapes.Pill)
            .background(c.ink, MikaShapes.Pill)
            .drawBehind {
                // The pill fills from the start edge as you hold, under the label.
                val w = size.width * progress.value
                if (w > 0f) drawRect(c.accent.copy(alpha = 0.28f), size = Size(w, size.height))
            }
            .border(1.dp, c.ink, MikaShapes.Pill)
            .alpha(if (enabled) 1f else 0.5f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (!active) return@awaitEachGesture
                    var done = false
                    val timer: Job = scope.launch {
                        progress.stop()
                        val from = progress.value
                        val start = withFrameNanos { it }
                        while (true) {
                            val now = withFrameNanos { it }
                            val p = (from + (now - start) / 1_000_000f / HOLD_MS).coerceAtMost(1f)
                            progress.snapTo(p)
                            if (p >= 1f) break
                        }
                        done = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        allow()
                    }
                    // Wait for the finger to lift (or the gesture to be taken away); early means cancel.
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    if (!done) {
                        timer.cancel()
                        scope.launch { progress.animateTo(0f, release) }
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = text
                stateDescription = if (progress.value > 0f && progress.value < 1f) holding else holdHint
                if (!enabled) disabled()
                onClick(label = text) { if (active) { allow(); true } else false }
            }
            .padding(horizontal = MikaSpace.S16, vertical = MikaSpace.S12),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8)) {
            HoldRing({ progress.value }, Modifier.size(MikaSize.TileIcon))
            Text(text, style = MikaType.BodyStrong, color = c.bg, textAlign = TextAlign.Center)
        }
    }
}

/** The ring that fills while holding: a faint full circle and an accent arc growing clockwise from the top. */
@Composable
private fun HoldRing(progress: () -> Float, modifier: Modifier) {
    val c = MikaTheme.colors
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2
        val box = Size(size.width - stroke, size.height - stroke)
        drawArc(c.bg.copy(alpha = 0.35f), 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
        drawArc(c.accent, -90f, 360f * progress(), false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** How long the hold takes: long enough that a brush or pocket tap can't approve, short enough to stay calm. */
const val HOLD_MS = 800
