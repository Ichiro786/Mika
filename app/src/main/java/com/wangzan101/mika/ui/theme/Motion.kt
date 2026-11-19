package com.wangzan101.mika.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Orbit's one motion system. Every animation in the app takes its timing from here, nowhere else.
 *
 * - Position, size and shape move on the **spatial** springs (slight overshoot-free settle, Material 3 Expressive style).
 * - Colour and opacity change on the **effects** springs (damping 1, never bounce).
 * - [Bouncy] is reserved for playful moments (a toggle landing, a pip popping in), never for content.
 * - Tweens exist only where a spring cannot express the motion: loops and the few timed reveals. They use
 *   the four [Duration] steps and the [Easing] curves.
 * - "Remove animations" (system animator scale 0) turns every spec into an instant [snap] from one place:
 *   [MotionSpecs] is built once per composition from [LocalReduceMotion], so a screen never checks it itself.
 *
 * Material 3's own `MotionScheme.expressive()` is not in the Material 3 version this app builds with
 * (Compose BOM 2024.12.01, material3 1.3.x); these tokens carry the same values so a later upgrade can map
 * them one to one without touching screens.
 */
object MikaMotion {
    /** Spring tokens: damping ratio and stiffness. */
    object Springs {
        const val SPATIAL_DAMPING = 0.9f
        const val SPATIAL_FAST = 1400f
        const val SPATIAL_DEFAULT = 700f
        const val SPATIAL_SLOW = 300f
        const val BOUNCY_DAMPING = 0.6f
        const val BOUNCY = 500f
        const val EFFECTS_DAMPING = 1f
        const val EFFECTS_FAST = 3800f
        const val EFFECTS_DEFAULT = 1600f
        const val EFFECTS_SLOW = 800f
    }

    /** Durations in milliseconds, for the few cases that need a tween. Nothing the user waits on exceeds [MEDIUM]. */
    object Duration {
        const val MICRO = 100
        const val SHORT = 200
        const val MEDIUM = 300
        const val LONG = 450
    }

    /** Material standard and emphasized curves. */
    object Easing {
        val Standard: androidx.compose.animation.core.Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val StandardDecelerate: androidx.compose.animation.core.Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
        val StandardAccelerate: androidx.compose.animation.core.Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
        val Emphasized: androidx.compose.animation.core.Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val EmphasizedDecelerate: androidx.compose.animation.core.Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        val EmphasizedAccelerate: androidx.compose.animation.core.Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
        val Linear: androidx.compose.animation.core.Easing = LinearEasing
    }

    /** Enter: fade + scale from [ENTER_SCALE] + a small upward slide of [ENTER_RISE] of the item height. */
    const val ENTER_SCALE = 0.94f
    const val ENTER_RISE = 0.06f
    /** Press feedback scale. */
    const val PRESS_SCALE = 0.96f
    /** List items stagger by this much, up to [STAGGER_MAX] items; later items come in together. */
    const val STAGGER_MS = 30
    const val STAGGER_MAX = 6

    /** The one loop period every loading indicator shares (pulse, spinner, shimmer, typing wave). */
    const val LOOP_MS = 1200

    /** Delay before the n-th item of a list starts its enter (0-based), capped at [STAGGER_MAX]. */
    fun staggerDelay(index: Int): Int = index.coerceIn(0, STAGGER_MAX - 1) * STAGGER_MS

    // Kept names from the earlier theme so existing call sites read the same.
    const val PAGE_MS = Duration.MEDIUM
    const val SWITCH_MS = Duration.SHORT
    val Decelerate: androidx.compose.animation.core.Easing = Easing.EmphasizedDecelerate

    /** For the few View-system animators (overlay windows): emphasized decelerate as a platform interpolator. */
    fun viewInterpolator(): android.view.animation.Interpolator = android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    fun specs(reduceMotion: Boolean): MotionSpecs = if (reduceMotion) MotionSpecs.Reduced else MotionSpecs.Full

    fun <T> spatial(reduceMotion: Boolean): FiniteAnimationSpec<T> = specs(reduceMotion).spatialDefault()
    fun <T> effects(reduceMotion: Boolean): FiniteAnimationSpec<T> = specs(reduceMotion).effectsDefault()
    fun <T> page(reduceMotion: Boolean): FiniteAnimationSpec<T> = specs(reduceMotion).spatialDefault()
    fun <T> switch(reduceMotion: Boolean): FiniteAnimationSpec<T> = specs(reduceMotion).spatialFast()
}

/**
 * The animation specs for the current composition. Reach them through [MikaTheme.motion]; when animations are
 * removed every function returns [snap] (or a still value), so call sites never branch on reduce-motion.
 */
@Immutable
class MotionSpecs private constructor(val reduced: Boolean) {
    private fun <T> springOf(damping: Float, stiffness: Float, threshold: T? = null): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = damping, stiffness = stiffness, visibilityThreshold = threshold)

    // Spatial: position, size, shape, scale.
    fun <T> spatialFast(threshold: T? = null): FiniteAnimationSpec<T> =
        springOf(MikaMotion.Springs.SPATIAL_DAMPING, MikaMotion.Springs.SPATIAL_FAST, threshold)
    fun <T> spatialDefault(threshold: T? = null): FiniteAnimationSpec<T> =
        springOf(MikaMotion.Springs.SPATIAL_DAMPING, MikaMotion.Springs.SPATIAL_DEFAULT, threshold)
    fun <T> spatialSlow(threshold: T? = null): FiniteAnimationSpec<T> =
        springOf(MikaMotion.Springs.SPATIAL_DAMPING, MikaMotion.Springs.SPATIAL_SLOW, threshold)
    /** Playful moments only. */
    fun <T> bouncy(threshold: T? = null): FiniteAnimationSpec<T> =
        springOf(MikaMotion.Springs.BOUNCY_DAMPING, MikaMotion.Springs.BOUNCY, threshold)

    // Effects: colour and alpha. Never bounce.
    fun <T> effectsFast(): FiniteAnimationSpec<T> = springOf(MikaMotion.Springs.EFFECTS_DAMPING, MikaMotion.Springs.EFFECTS_FAST)
    fun <T> effectsDefault(): FiniteAnimationSpec<T> = springOf(MikaMotion.Springs.EFFECTS_DAMPING, MikaMotion.Springs.EFFECTS_DEFAULT)
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = springOf(MikaMotion.Springs.EFFECTS_DAMPING, MikaMotion.Springs.EFFECTS_SLOW)

    /** A timed tween on the duration scale, for the rare motion a spring cannot express. */
    fun <T> timed(
        durationMs: Int,
        easing: Easing = MikaMotion.Easing.Standard,
        delayMs: Int = 0,
    ): FiniteAnimationSpec<T> = if (reduced) snap() else tween(durationMs, delayMs, easing)

    /** Spatial offset spring with the right pixel threshold, for slides. */
    fun offset(): FiniteAnimationSpec<IntOffset> = spatialDefault(IntOffset.VisibilityThreshold)
    fun size(): FiniteAnimationSpec<IntSize> = spatialDefault(IntSize.VisibilityThreshold)

    /** Standard enter: fade + 0.94→1 scale + small upward slide. [delayMs] is for staggered lists. */
    fun enter(origin: TransformOrigin = TransformOrigin.Center, delayMs: Int = 0): EnterTransition =
        if (reduced) EnterTransition.None
        else fadeIn(if (delayMs == 0) effectsDefault() else timed(MikaMotion.Duration.SHORT, delayMs = delayMs)) +
            scaleIn(spatialDefault(), initialScale = MikaMotion.ENTER_SCALE, transformOrigin = origin) +
            slideInVertically(offset()) { (it * MikaMotion.ENTER_RISE).toInt() }

    /** Standard exit: faster than enter, a plain fade and a small shrink. */
    fun exit(origin: TransformOrigin = TransformOrigin.Center): ExitTransition =
        if (reduced) ExitTransition.None
        else fadeOut(effectsFast()) + scaleOut(spatialFast(), targetScale = MikaMotion.ENTER_SCALE, transformOrigin = origin)

    /** Fade only, for text and content that must not move. */
    fun fadeEnter(): EnterTransition = if (reduced) EnterTransition.None else fadeIn(effectsDefault())
    fun fadeExit(): ExitTransition = if (reduced) ExitTransition.None else fadeOut(effectsFast())

    companion object {
        val Full = MotionSpecs(reduced = false)
        val Reduced = MotionSpecs(reduced = true)
    }
}

/** The motion specs for this composition; snaps everything when the system says "Remove animations". */
val MikaTheme.motion: MotionSpecs
    @Composable @ReadOnlyComposable get() = MikaMotion.specs(LocalReduceMotion.current)

/** System "Remove animations" (animator duration scale 0). */
fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** [animationsOff], kept current: toggling the system switch reaches open screens without recreating them. */
@Composable
fun rememberAnimationsOff(): Boolean {
    val context = LocalContext.current
    val state = remember(context) { mutableStateOf(animationsOff(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { state.value = animationsOff(context) }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        runCatching { context.contentResolver.registerContentObserver(uri, false, observer) }
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }
    return state.value
}

/**
 * The one shared loading pulse, 0→1→0 over [MikaMotion.LOOP_MS]. Every breathing dot, halo and shimmer uses
 * it. Returns [still] when animations are removed.
 */
@Composable
fun rememberMikaPulse(still: Float = 1f, label: String = "pulse"): State<Float> {
    if (LocalReduceMotion.current) return rememberUpdatedState(still)
    return rememberInfiniteTransition(label = label).animateFloat(
        0f, 1f, MikaMotion.pulseSpec(), label = label,
    )
}

/**
 * The one shared progress phase, 0→1 linear and restarting over [MikaMotion.LOOP_MS]; spinners turn it ×360,
 * shimmers and the typing wave move along it. Returns null when animations are removed, so callers draw the
 * still state.
 */
@Composable
fun rememberMikaPhase(label: String = "phase"): State<Float>? {
    if (LocalReduceMotion.current) return null
    return rememberInfiniteTransition(label = label).animateFloat(
        0f, 1f, MikaMotion.phaseSpec(), label = label,
    )
}

/**
 * A loop that lasts [beats] × [MikaMotion.LOOP_MS]: ambient identity motion (the presence orb) runs at whole or
 * half beats of the shared loop so everything that moves on screen keeps one rhythm. [reverse] swings back and
 * forth on the standard curve; otherwise it is a linear restarting phase.
 */
fun MikaMotion.loop(beats: Float = 1f, reverse: Boolean = false): InfiniteRepeatableSpec<Float> {
    val ms = (LOOP_MS * beats).toInt()
    return if (reverse) infiniteRepeatable(tween(ms, easing = MikaMotion.Easing.Standard), RepeatMode.Reverse)
    else infiniteRepeatable(tween(ms, easing = MikaMotion.Easing.Linear), RepeatMode.Restart)
}

internal fun MikaMotion.pulseSpec(): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(tween(LOOP_MS / 2, easing = MikaMotion.Easing.Standard), RepeatMode.Reverse)

internal fun MikaMotion.phaseSpec(): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(tween(LOOP_MS, easing = MikaMotion.Easing.Linear), RepeatMode.Restart)

/**
 * Press feedback: scales to [MikaMotion.PRESS_SCALE] on the fast spatial spring while pressed. Pass the same
 * [interactionSource] the clickable uses.
 */
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) MikaMotion.PRESS_SCALE else 1f, MikaTheme.motion.spatialFast(), label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}
