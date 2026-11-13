package com.wangzan101.mika.ui.identity

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.settings.OrbitIdentityMode

enum class PresenceState { Idle, Listening, Thinking, Working, NeedsUser, Success, Error }

/** Decorative presence. A caller supplies the visible or accessible status text. */
@Composable
fun MikaPresenceAvatar(
    modifier: Modifier = Modifier,
    state: PresenceState = PresenceState.Idle,
    dark: Boolean = MikaTheme.colors.isDark,
    reduceMotion: Boolean = MikaTheme.reduceMotion,
    framed: Boolean = false,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val animate = started && !reduceMotion
    val continuous = state == PresenceState.Idle || state == PresenceState.Listening ||
        state == PresenceState.Thinking || state == PresenceState.Working
    val phase = if (animate && continuous) {
        val transition = rememberInfiniteTransition(label = "presence")
        transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(if (state == PresenceState.Idle) 2600 else 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ), label = "presence-phase",
        )
    } else null
    val pulse = remember { Animatable(0f) }
    var pulsed by remember { mutableStateOf<PresenceState?>(null) }
    LaunchedEffect(state, animate) {
        pulse.snapTo(0f)
        if (state != PresenceState.NeedsUser && state != PresenceState.Success) pulsed = null
        if (animate && (state == PresenceState.NeedsUser || state == PresenceState.Success) && pulsed != state) {
            pulsed = state
            pulse.animateTo(1f, tween(350))
            pulse.animateTo(0f, tween(600))
        }
    }
    val coral = orbitIdentityPalette(MikaTheme.identity, dark).glow
    val error = if (MikaTheme.identity == OrbitIdentityMode.Monochrome) coral else MikaTheme.colors.bad
    val surface = MikaTheme.colors.surface
    val edge = MikaTheme.colors.hair
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val p = phase?.value ?: 0f
            if (framed) {
                drawCircle(
                    Brush.radialGradient(
                        0f to coral.copy(alpha = if (dark) 0.13f + p * 0.02f else 0.09f + p * 0.02f).compositeOver(surface),
                        0.68f to surface,
                        1f to surface,
                        center = Offset(size.width * 0.5f, size.height * 0.56f),
                        radius = size.minDimension * 0.55f,
                    ),
                )
                drawCircle(edge, style = Stroke(width = size.minDimension * 0.015f))
            }
            val ring = when (state) {
                PresenceState.Listening -> 0.12f + p * 0.12f
                PresenceState.Working -> 0.20f
                PresenceState.NeedsUser, PresenceState.Success -> 0.10f + pulse.value * 0.18f
                else -> 0f
            }
            if (ring > 0f) drawCircle(coral.copy(alpha = ring), radius = size.minDimension * (0.43f + p * 0.04f), style = Stroke(width = size.minDimension * 0.035f))
            if (state == PresenceState.Error) {
                drawCircle(error, radius = size.minDimension * 0.07f, center = Offset(size.width * 0.83f, size.height * 0.20f))
            }
            if (state == PresenceState.Working) {
                val side = size.minDimension * 0.82f
                drawArc(coral.copy(alpha = 0.5f), -35f + p * 60f, 110f, false,
                    topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                    size = Size(side, side), style = Stroke(width = size.minDimension * 0.035f))
            }
        }
        MikaMark(Modifier.fillMaxSize(if (framed) 0.58f else 1f).graphicsLayer {
            val p = phase?.value ?: 0f
            val breath = if (state == PresenceState.Idle) 1f + p * 0.025f else 1f
            scaleX = breath
            scaleY = breath
            translationX = if (state == PresenceState.Thinking && phase != null) (p - 0.5f) * size.width * 0.05f else 0f
        }, dark)
    }
}
