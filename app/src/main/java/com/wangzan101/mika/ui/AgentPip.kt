package com.wangzan101.mika.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.cap.phone.display.PipInput
import com.wangzan101.mika.ui.components.floatShadow
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * How a running task shows while Orbit works in the background. Nothing floats over the chat any more:
 * - a small frosted pill docks centred above the message bar, with a tiny live thumbnail, a live dot,
 *   what Orbit is doing ([label]) and the step count and time;
 * - tapping it ([onExpand]) springs open a frosted live-view sheet with the whole virtual screen and
 *   Hide / Stop (plus "I'll handle it" when Orbit needs you); swipe the sheet down or tap outside to hide it;
 * - when Orbit needs you the pill turns amber and breathes, and tapping it goes to the question ([onAttention]);
 * - after [onClose] only a small "Show screen" chip stays in the same place until the task ends.
 * [reduceMotion] turns off the springs, slides and breathing.
 * [bottomInset] is the height of the message bar in pixels; the pill sits just above it.
 * [topInset] is kept for callers and no longer used for placement.
 */
@Composable
fun AgentPip(
    pip: PipUi,
    frame: Bitmap?,
    webContent: Boolean,
    /** Lets the user tap and swipe on the virtual screen inside the live view; null while there is none. */
    pipInput: PipInput? = null,
    /** The user turned on "let me operate the small view directly" in Settings. */
    operateEnabled: Boolean = false,
    reduceMotion: Boolean,
    topInset: Float,
    bottomInset: Float,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onAttention: () -> Unit,
    onClose: () -> Unit = {},
    onReopen: () -> Unit = {},
    onUnmute: () -> Unit = {},
    /** What Orbit is doing right now, in the user's words ("Reading BBVM 12th PCM"). */
    label: String = "",
    /** Steps taken so far; 0 hides the count. */
    steps: Int = 0,
    /** When the task started (epoch ms); 0 hides the clock. */
    startedAt: Long = 0L,
    /** Stop the task; null hides the button. */
    onStop: (() -> Unit)? = null,
    /** Take over the step Orbit is waiting on; shown only while it needs you. */
    onTakeControl: (() -> Unit)? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val bottom = with(density) { bottomInset.toDp() }
        val fade = tween<Float>(if (reduceMotion) 150 else 260)
        val needsYou = pip.mode == PipMode.NeedsYou
        val elapsed = rememberElapsed(startedAt)
        val meta = listOfNotNull(
            steps.takeIf { it > 0 }?.let { stringResource(R.string.pip_step, it) },
            elapsed?.let { ChatCopy.clock(it) },
        ).joinToString(" · ")

        // Closed for this task: only a small chip stays where the pill was (not during the last seconds of a finished task)
        AnimatedVisibility(
            pip.visible && pip.closed && pip.mode != PipMode.Done, Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(fade), exit = fadeOut(fade),
        ) {
            ClosedPip(needsYou, reduceMotion, Modifier.padding(bottom = bottom + 10.dp), onReopen, onAttention)
        }

        val springIn = if (reduceMotion) fadeIn(fade) else
            fadeIn(fade) + scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.7f) +
                slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { it }
        val springOut = if (reduceMotion) fadeOut(fade) else fadeOut(fade) + scaleOut(tween(200), targetScale = 0.85f)
        AnimatedVisibility(
            pip.visible && !pip.closed && !pip.expanded, Modifier.align(Alignment.BottomCenter),
            enter = springIn, exit = springOut,
        ) {
            AgentPill(
                mode = pip.mode,
                frame = frame,
                title = if (needsYou) stringResource(R.string.pip_closed_needs_you) else label,
                meta = if (needsYou) label else meta,
                reduceMotion = reduceMotion,
                modifier = Modifier.padding(bottom = bottom + 10.dp, start = 16.dp, end = 16.dp),
                onClick = { if (needsYou) onAttention() else onExpand() },
            )
        }

        // Live view: dimmed scrim (tap to hide) and a frosted sheet from the bottom
        AnimatedVisibility(pip.visible && !pip.closed && pip.expanded, enter = fadeIn(fade), exit = fadeOut(fade)) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) { detectTapGestures { onCollapse() } },
            )
        }
        val sheetHeight = with(density) { (constraints.maxHeight * 0.78f).toDp() }
        AnimatedVisibility(
            pip.visible && !pip.closed && pip.expanded, Modifier.align(Alignment.BottomCenter),
            enter = if (reduceMotion) fadeIn(fade) else
                slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(fade),
            exit = if (reduceMotion) fadeOut(fade) else slideOutVertically(tween(260)) { it } + fadeOut(fade),
        ) {
            LiveSheet(
                pip = pip,
                frame = frame,
                pipInput = pipInput,
                operateEnabled = operateEnabled,
                reduceMotion = reduceMotion,
                height = sheetHeight,
                title = if (needsYou) stringResource(R.string.pip_closed_needs_you) else label,
                meta = meta,
                onCollapse = onCollapse,
                onClose = onClose,
                onUnmute = onUnmute,
                onStop = onStop,
                onTakeControl = if (needsYou) onTakeControl else null,
            )
        }
    }
}

/** Gentle breathing (1 → [low] → 1) for the needs-you glow; constant 1 with reduced motion or when [on] is false. */
@Composable
private fun breath(on: Boolean, reduceMotion: Boolean, low: Float, label: String): Float =
    if (on && !reduceMotion) {
        rememberInfiniteTransition(label = label).animateFloat(
            1f, low, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = label,
        ).value
    } else 1f

@Composable
private fun AgentPill(
    mode: PipMode,
    frame: Bitmap?,
    title: String,
    meta: String,
    reduceMotion: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = MikaTheme.colors
    val shape = RoundedCornerShape(50)
    val needsYou = mode == PipMode.NeedsYou
    val tone by animateColorAsState(
        when (mode) {
            PipMode.Running -> c.ok
            PipMode.NeedsYou -> c.warnSolid
            PipMode.Done -> c.ink3
        },
        tween(if (reduceMotion) 0 else 400), label = "pillTone",
    )
    val glow = breath(needsYou, reduceMotion, 0.35f, "pillBreath")
    val live = breath(mode == PipMode.Running, reduceMotion, 0.45f, "pillLive")
    Box(modifier) {
        if (needsYou) {
            // Soft amber halo behind the pill (no blur below Android 12, just a faint ring)
            Box(
                Modifier.matchParentSize().alpha(glow * 0.6f).blur(14.dp).background(c.warnSolid.copy(alpha = 0.45f), shape),
            )
        }
        Row(
            Modifier.widthIn(max = 320.dp)
                .floatShadow(c, shape)
                .background(c.glass, shape)
                .border(if (needsYou) 1.dp else 0.5.dp, if (needsYou) c.warnSolid.copy(alpha = 0.4f + 0.4f * glow) else c.glassEdge, shape)
                .clip(shape)
                .clickable(role = Role.Button, onClick = onClick)
                .alpha(if (mode == PipMode.Done) 0.85f else 1f)
                .padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp)
                .testTag("pip-pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val thumb = RoundedCornerShape(9.dp)
            Box(
                Modifier.size(28.dp).clip(thumb).background(c.surface).border(1.5.dp, tone.copy(alpha = 0.55f), thumb),
                contentAlignment = Alignment.Center,
            ) {
                if (frame != null) {
                    val image = remember(frame) { frame.asImageBitmap() }
                    Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                } else {
                    Icon(MikaIcons.Screens, null, Modifier.size(14.dp), tint = c.ink2)
                }
            }
            Box(Modifier.size(7.dp).alpha(if (mode == PipMode.Running) 0.55f + 0.45f * live else glow).background(tone, CircleShape))
            if (title.isNotBlank()) {
                Text(
                    title, style = MikaType.Label, color = if (needsYou) c.warn else c.ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (meta.isNotBlank()) {
                Text(meta, style = MikaType.Hint, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LiveSheet(
    pip: PipUi,
    frame: Bitmap?,
    pipInput: PipInput?,
    operateEnabled: Boolean,
    reduceMotion: Boolean,
    height: Dp,
    title: String,
    meta: String,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onUnmute: () -> Unit,
    onStop: (() -> Unit)?,
    onTakeControl: (() -> Unit)?,
) {
    val c = MikaTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val hideAt = with(density) { 120.dp.toPx() }
    val dragState = rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) } }
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val needsYou = pip.mode == PipMode.NeedsYou
    val tone = if (needsYou) c.warnSolid else c.ok
    val glow = breath(needsYou, reduceMotion, 0.5f, "sheetBreath")
    val swipeHint = stringResource(R.string.pip_swipe_hide)
    // Taps and swipes go to the virtual screen only when the user allowed it and the screen is live
    val operateInput = pipInput.takeIf { operateEnabled && frame != null }

    Column(
        Modifier.fillMaxWidth().height(height)
            .offset { IntOffset(0, drag.value.roundToInt()) }
            .floatShadow(c, shape)
            .background(c.glass, shape)
            .border(0.5.dp, c.glassEdge, shape)
            .clip(shape)
            // Swallow taps so they don't reach the scrim behind
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 14.dp, end = 14.dp, top = 10.dp)
            .padding(bottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() } + 12.dp)
            .testTag("pip-sheet"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Grab handle and header: drag down to hide
        Column(
            Modifier.fillMaxWidth()
                .draggable(
                    dragState, Orientation.Vertical,
                    onDragStopped = { v ->
                        if (drag.value > hideAt || v > 1800f) onCollapse()
                        scope.launch { if (reduceMotion) drag.snapTo(0f) else drag.animateTo(0f, spring(dampingRatio = 0.7f)) }
                    },
                )
                .semantics { contentDescription = swipeHint },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).background(c.ink3.copy(alpha = 0.6f), CircleShape))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).alpha(glow).background(tone, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    title, style = MikaType.BodyStrong, color = if (needsYou) c.warn else c.ink, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (meta.isNotBlank()) Text(meta, style = MikaType.Caption, color = c.ink2, maxLines = 1)
            }
        }

        // The live screen with a soft glow instead of a hard border
        val liveShape = RoundedCornerShape(20.dp)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.matchParentSize().padding(6.dp).alpha(0.5f * glow).blur(18.dp).background(tone.copy(alpha = 0.35f), liveShape))
            BoxWithConstraints(
                Modifier.matchParentSize().clip(liveShape).background(c.surface)
                    .border(1.dp, tone.copy(alpha = 0.3f + 0.2f * glow), liveShape)
                    .testTag("pip-window"),
                contentAlignment = Alignment.Center,
            ) {
                if (frame != null) {
                    val image = remember(frame) { frame.asImageBitmap() }
                    val fit = fittedSize(frame.width, frame.height, maxWidth, maxHeight)
                    Box(
                        Modifier.size(fit.first, fit.second).testTag("pip-frame")
                            .then(if (operateInput != null) operateInputModifier(operateInput, scope) else Modifier),
                    ) {
                        Image(image, stringResource(R.string.pip_screen), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                } else {
                    Icon(MikaIcons.Screens, stringResource(R.string.pip_screen), Modifier.size(28.dp), tint = c.ink3)
                }
                Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (operateInput != null) {
                        PipButton(MikaIcons.Back, stringResource(R.string.pip_back), { scope.launch { operateInput.back() } }, "pip-back")
                    }
                    PipButton(MikaIcons.Sound, stringResource(R.string.pip_unmute), onUnmute, "pip-unmute")
                    PipButton(MikaIcons.EyeOff, stringResource(R.string.pip_close), onClose, "pip-close")
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onTakeControl != null) SheetAction(stringResource(R.string.action_take_over), c.warnSoft, c.warn, "pip-take-control", onTakeControl)
            SheetAction(stringResource(R.string.hide), c.fill, c.ink, "pip-hide", onCollapse)
            if (onStop != null) SheetAction(stringResource(R.string.chat_stop), c.badSoft, c.bad, "pip-stop", onStop)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SheetAction(text: String, bg: Color, fg: Color, tag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.weight(1f).height(44.dp).clip(shape).background(bg, shape)
            .clickable(role = Role.Button, onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MikaType.Label, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/**
 * After the preview is closed: a small chip in the pill's place. Normally "Show screen" (reopens);
 * when Orbit needs you it turns amber, breathes and says "Needs you", and goes to the question.
 */
@Composable
private fun ClosedPip(needsYou: Boolean, reduceMotion: Boolean, modifier: Modifier, onReopen: () -> Unit, onAttention: () -> Unit) {
    val c = MikaTheme.colors
    val shape = RoundedCornerShape(50)
    val glow = breath(needsYou, reduceMotion, 0.45f, "closedBreathe")
    val label = stringResource(if (needsYou) R.string.pip_closed_needs_you else R.string.pip_reopen)
    Row(
        modifier.floatShadow(c, shape).background(c.glass, shape)
            .border(if (needsYou) 1.5.dp else 0.5.dp, if (needsYou) c.warnSolid.copy(alpha = glow) else c.glassEdge, shape)
            .clip(shape)
            .clickable(onClickLabel = label, role = Role.Button) { if (needsYou) onAttention() else onReopen() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("pip-closed"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (needsYou) Box(Modifier.size(7.dp).alpha(glow).background(c.warnSolid, CircleShape))
        else Icon(MikaIcons.Screens, null, Modifier.size(14.dp), tint = c.ink2)
        Text(label, style = MikaType.Hint, color = if (needsYou) c.warn else c.ink2)
    }
}

@Composable
private fun PipButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tag: String) {
    Box(
        Modifier.size(32.dp)
            .floatShadow(MikaTheme.colors, CircleShape).background(MikaTheme.colors.glass, CircleShape).clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, Modifier.size(15.dp), tint = MikaTheme.colors.ink) }
}

/** The frame fitted into innerW×innerH by aspect ratio (no crop, no stretch); the whole box when sizes are unknown. */
private fun fittedSize(frameW: Int, frameH: Int, innerW: Dp, innerH: Dp): Pair<Dp, Dp> {
    if (frameW <= 0 || frameH <= 0) return innerW to innerH
    val a = frameW.toFloat() / frameH
    val w = if (innerW / a <= innerH) innerW else innerH * a
    return w to w / a
}

/** Direct operation in the live view: a press without movement taps, a movement swipes along its points (0..1 in this box). */
private fun operateInputModifier(input: PipInput, scope: CoroutineScope): Modifier = Modifier.pointerInput(input) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val points = mutableListOf(down.position)
        var lastUptime = down.uptimeMillis
        var moved = false
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            change.consume()
            points += change.position
            lastUptime = change.uptimeMillis
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
        }
        val pts = points.map { p ->
            (p.x / size.width).coerceIn(0f, 1f) to (p.y / size.height).coerceIn(0f, 1f)
        }
        if (!moved) {
            val (fx, fy) = pts.last()
            scope.launch { input.tap(fx, fy) }
        } else {
            val duration = (lastUptime - down.uptimeMillis).coerceIn(80L, 600L)
            scope.launch { input.swipe(pts, duration) }
        }
    }
}
