package com.wangzan101.mika.ui.identity

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wangzan101.mika.settings.OrbitIdentityMode
import com.wangzan101.mika.ui.theme.MikaTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class PresenceState { Idle, Listening, Thinking, Working, NeedsUser, Success, Error }

/** Loop length of each living state; success and error settle instead of looping. */
private fun loopMs(state: PresenceState): Int? = when (state) {
    PresenceState.Idle -> 3600
    PresenceState.Listening -> 1800
    PresenceState.Thinking -> 1500
    PresenceState.Working -> 1700
    PresenceState.NeedsUser -> 2000
    PresenceState.Success, PresenceState.Error -> null
}

/** A smooth 0..1..0 swing over one loop, so a restarting phase never jumps. */
private fun swing(p: Float): Float = sin(2f * PI.toFloat() * p)

/** Where the sphere of the mark sits inside the avatar box, and the box's own size. */
private class OrbGeometry(val cx: Float, val cy: Float, val r: Float, val box: Float, val mark: Float)

private fun DrawScope.orbGeometry(markScale: Float): OrbGeometry {
    val mark = size.minDimension * markScale
    // The mark's sphere is drawn at (55, 46) with radius 24 on its 100-unit canvas.
    return OrbGeometry(size.width / 2f + mark * 0.05f, size.height / 2f - mark * 0.04f, mark * 0.24f, size.minDimension, mark)
}

/**
 * Decorative presence after the presence study: idle breathes, listening ripples, thinking trails,
 * working orbits, needs-you sends out rays, success settles inside a halo. A caller supplies the
 * visible or accessible status text. One phase float drives every loop and is only read while
 * drawing, so the avatar never recomposes per frame; reduced motion shows each state as a still.
 */
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
    val loop = if (animate) loopMs(state) else null
    val phase: State<Float>? = if (loop != null) key(loop) {
        rememberInfiniteTransition(label = "presence").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(loop, easing = LinearEasing)),
            label = "presence-phase",
        )
    } else null
    // Each new state grows in (rings, rays, halo) instead of popping; success uses it to settle.
    val arrive = remember { Animatable(1f) }
    LaunchedEffect(state, animate) {
        if (animate) {
            arrive.snapTo(0f)
            arrive.animateTo(1f, tween(if (state == PresenceState.Success) 700 else 420, easing = FastOutSlowInEasing))
        } else arrive.snapTo(1f)
    }
    val glow = orbitIdentityPalette(MikaTheme.identity, dark).glow
    val error = if (MikaTheme.identity == OrbitIdentityMode.Monochrome) glow else MikaTheme.colors.bad
    val surface = MikaTheme.colors.surface
    val edge = MikaTheme.colors.hair
    val markScale = if (framed) 0.58f else 1f
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val p = phase?.value ?: 0f
            val a = arrive.value
            val g = orbGeometry(markScale)
            if (framed) {
                val base = when (state) {
                    PresenceState.NeedsUser -> if (dark) 0.22f else 0.16f
                    PresenceState.Success -> if (dark) 0.20f else 0.14f
                    else -> if (dark) 0.13f else 0.09f
                }
                val breath = if (state == PresenceState.Idle && phase != null) swing(p) * 0.02f else 0f
                drawCircle(
                    Brush.radialGradient(
                        0f to glow.copy(alpha = base + breath).compositeOver(surface),
                        0.68f to surface,
                        1f to surface,
                        center = Offset(size.width * 0.5f, size.height * 0.56f),
                        radius = size.minDimension * 0.55f,
                    ),
                )
                drawCircle(edge, style = Stroke(width = g.box * 0.015f))
            }
            behind(state, g, p, phase != null, a, glow)
        }
        MikaMark(Modifier.fillMaxSize(markScale).graphicsLayer {
            val moving = phase != null
            val p = phase?.value ?: 0f
            val scale = when (state) {
                PresenceState.Idle -> if (moving) 1f + swing(p) * 0.022f else 1f
                PresenceState.Success -> 0.92f + 0.08f * arrive.value
                else -> 1f
            }
            scaleX = scale
            scaleY = scale
            translationX = if (state == PresenceState.Thinking && moving) swing(p) * size.width * 0.025f else 0f
        }, dark)
        Canvas(Modifier.matchParentSize()) {
            val p = phase?.value ?: 0f
            front(state, orbGeometry(markScale), p, phase != null, arrive.value, glow, error)
        }
    }
}

/** Effects under the mark: ripples, trails, the back of the orbit ring, halo and glow. */
private fun DrawScope.behind(state: PresenceState, g: OrbGeometry, p: Float, moving: Boolean, a: Float, glow: Color) {
    val center = Offset(g.cx, g.cy)
    when (state) {
        PresenceState.Listening -> {
            val inner = g.r * 1.15f
            val outer = g.box * 0.48f
            repeat(3) { k ->
                val t = if (moving) (p + k / 3f) % 1f else (k + 0.5f) / 3f
                drawCircle(glow.copy(alpha = 0.4f * (1f - t) * a), inner + (outer - inner) * t, center,
                    style = Stroke(width = g.box * 0.022f))
            }
        }
        PresenceState.Thinking -> {
            val sway = if (moving) 0.75f + 0.25f * swing(p) else 1f
            for (k in 2 downTo 1) {
                drawCircle(glow.copy(alpha = 0.24f / k * a), g.r, Offset(g.cx - g.r * 0.55f * k * sway, g.cy))
            }
        }
        PresenceState.Working -> orbit(g) { topLeft, oval ->
            drawOval(glow.copy(alpha = 0.24f * a), topLeft, oval, style = Stroke(width = g.box * 0.02f))
        }
        PresenceState.NeedsUser -> {
            val pulse = if (moving) 0.85f + 0.15f * swing(p) else 1f
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.3f * pulse * a), glow.copy(alpha = 0f)), center, g.r * 2f),
                g.r * 2f, center)
        }
        PresenceState.Success -> {
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.26f * a), glow.copy(alpha = 0f)), center, g.r * 2.2f),
                g.r * 2.2f, center)
            drawCircle(glow.copy(alpha = 0.5f * a), g.r * (1.25f + 0.4f * a), center, style = Stroke(width = g.box * 0.024f))
        }
        else -> Unit
    }
}

/** Effects over the mark: the bright sweep of the orbit, the attention rays and the error dot. */
private fun DrawScope.front(state: PresenceState, g: OrbGeometry, p: Float, moving: Boolean, a: Float, glow: Color, error: Color) {
    when (state) {
        PresenceState.Working -> orbit(g) { topLeft, oval ->
            drawArc(glow.copy(alpha = 0.9f * a), if (moving) p * 360f else 200f, 70f, false, topLeft, oval,
                style = Stroke(width = g.box * 0.03f, cap = StrokeCap.Round))
        }
        PresenceState.NeedsUser -> {
            // Three short rays at one to two o'clock, as in the study.
            val reach = if (moving) 0.8f + 0.2f * swing(p) else 1f
            for (deg in floatArrayOf(-72f, -50f, -28f)) {
                val rad = deg * PI.toFloat() / 180f
                val dir = Offset(cos(rad), sin(rad))
                val start = Offset(g.cx, g.cy) + dir * (g.r * 1.32f)
                drawLine(glow.copy(alpha = 0.95f * a), start, start + dir * (g.r * 0.5f * reach * a),
                    strokeWidth = g.r * 0.17f, cap = StrokeCap.Round)
            }
        }
        PresenceState.Error -> drawCircle(error, radius = g.box * 0.07f, center = Offset(size.width * 0.83f, size.height * 0.20f))
        else -> Unit
    }
}

/** A tilted elliptical orbit around the mark, shared by the back ring and the front sweep. */
private inline fun DrawScope.orbit(g: OrbGeometry, draw: DrawScope.(Offset, Size) -> Unit) {
    val oval = Size(minOf(g.mark * 1.04f, g.box * 0.96f), g.mark * 0.34f)
    val pivot = Offset(size.width / 2f, size.height / 2f + g.mark * 0.02f)
    rotate(-16f, pivot) { draw(Offset(pivot.x - oval.width / 2f, pivot.y - oval.height / 2f), oval) }
}
