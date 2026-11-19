package com.wangzan101.mika.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.ChatCopy
import com.wangzan101.mika.ui.rememberElapsed
import com.wangzan101.mika.ui.theme.MikaColors
import com.wangzan101.mika.ui.theme.MikaMotion
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaSpace
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.ui.theme.motion
import com.wangzan101.mika.ui.theme.rememberMikaPhase

/**
 * The one live plan card. Chat, the floating capsule card and the mini view all draw a task's progress with it;
 * the capsule pill uses [LivePlanPill], its compact form.
 *
 * Header: the task, "Step 3" (or "Step 3 of 5" once a plan's length is known), the elapsed time and Stop.
 * Every step is visible while it runs: pending (dim dot), running (spinning ring), done (a check that draws
 * itself), failed (red mark and its one-line reason), waiting or needing approval (amber, saying what for).
 * Approval itself stays on the keyed confirmation card under the plan; this card never approves anything.
 *
 * [maxStepsHeight] bounds the step list (floating card, mini view): it then scrolls on its own, keeps the current
 * step in view while the user is at the bottom, and fades out at the top. Unbounded (chat), the newest
 * [ChatCopy.VISIBLE_STEPS] rows show and earlier ones fold into one "N earlier steps" row.
 */
@Composable
fun LivePlanCard(
    plan: LivePlan,
    modifier: Modifier = Modifier,
    onStop: (() -> Unit)? = null,
    maxStepsHeight: Dp? = null,
    showHeader: Boolean = true,
    showTitle: Boolean = true,
) {
    Column(modifier.fillMaxWidth().testTag("live-plan")) {
        if (showHeader) PlanHeader(plan, onStop, showTitle)
        if (plan.steps.isNotEmpty()) {
            if (maxStepsHeight != null) BoundedSteps(plan, maxStepsHeight, if (showHeader) MikaSpace.S8 else 0.dp)
            else FoldedSteps(plan, Modifier.padding(top = if (showHeader) MikaSpace.S8 else 0.dp))
        }
    }
}

/** The steps alone, every one shown: the expanded part of a finished task's one-line summary. */
@Composable
fun LivePlanSteps(plan: LivePlan, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) { StepRows(plan, plan.steps, animateFrom = Int.MAX_VALUE) }
}

/** Header: status ring, title, "Step 3 · 0:42", Stop. */
@Composable
private fun PlanHeader(plan: LivePlan, onStop: (() -> Unit)?, showTitle: Boolean) {
    val c = MikaTheme.colors
    val elapsed = if (plan.active) rememberElapsed(plan.startedAt) else plan.elapsedMs
    val stepText = when {
        plan.stepNumber <= 0 -> null
        plan.total != null -> stringResource(R.string.plan_step_of, plan.stepNumber, plan.total)
        else -> stringResource(R.string.pip_step, plan.stepNumber)
    }
    val meta = listOfNotNull(stepText, elapsed?.let(ChatCopy::clock)).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().heightIn(min = MikaSize.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12),
    ) {
        PlanRing(plan, Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            if (showTitle && plan.title.isNotEmpty()) Text(
                plan.title, Modifier.semantics { heading() }, style = MikaType.BodyStrong, color = c.ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) Text(
                meta, Modifier.testTag("live-plan-meta"),
                style = MikaType.Caption, color = if (showTitle) c.ink3 else c.ink2,
            )
        }
        if (onStop != null && plan.active) StopButton(onStop)
    }
}

@Composable
private fun StopButton(onStop: () -> Unit) {
    val c = MikaTheme.colors
    val label = stringResource(R.string.chat_stop)
    Box(
        Modifier.heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onStop)
            .testTag("live-plan-stop"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, Modifier.background(c.fill, MikaShapes.Pill).padding(horizontal = MikaSpace.S12, vertical = 6.dp),
            style = MikaType.Label, color = c.ink,
        )
    }
}

/** Chat: the newest rows, earlier ones folded behind one tappable row. */
@Composable
private fun FoldedSteps(plan: LivePlan, modifier: Modifier) {
    val c = MikaTheme.colors
    var all by rememberSaveable { mutableStateOf(false) }
    val hidden = if (all) 0 else (plan.steps.size - ChatCopy.VISIBLE_STEPS).coerceAtLeast(0)
    // Rows already there when the card first shows don't animate; only rows that arrive later do.
    val firstCount = remember { plan.steps.size }
    Column(modifier.fillMaxWidth()) {
        if (hidden > 0) {
            Text(
                ChatCopy.earlierSteps(hidden),
                Modifier.fillMaxWidth().heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Tile)
                    .clickable(role = Role.Button) { all = true }
                    .padding(start = RailWidth + MikaSpace.S8, top = MikaSpace.S12),
                style = MikaType.Caption, color = c.accent,
            )
        }
        StepRows(plan, plan.steps.drop(hidden), animateFrom = firstCount)
    }
}

/** Floating card and mini view: its own scroll, the current step kept in view, older steps fading at the top. */
@Composable
private fun BoundedSteps(plan: LivePlan, max: Dp, top: Dp) {
    val scroll = rememberScrollState()
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(scroll) {
        var wasScrolling = false
        snapshotFlow { scroll.isScrollInProgress to (scroll.value >= scroll.maxValue) }.collect { (scrolling, atEnd) ->
            if (scrolling || wasScrolling) following = atEnd
            wasScrolling = scrolling
        }
    }
    val motion = MikaTheme.motion
    LaunchedEffect(plan.steps.size, scroll.maxValue) {
        if (following && !scroll.isScrollInProgress) scroll.animateScrollTo(scroll.maxValue, motion.spatialDefault())
    }
    val fade = with(LocalDensity.current) { FadeEdge.toPx() }
    val firstCount = remember { plan.steps.size }
    Column(
        Modifier.padding(top = top).fillMaxWidth().heightIn(max = max).testTag("live-plan-steps")
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                if (scroll.value > 0) drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = fade), blendMode = BlendMode.DstIn,
                )
            }
            .verticalScroll(scroll),
    ) { StepRows(plan, plan.steps, animateFrom = firstCount) }
}

@Composable
private fun StepRows(plan: LivePlan, rows: List<PlanStep>, animateFrom: Int) {
    val expanded = remember { mutableStateMapOf<Int, Boolean>() }
    val lastId = plan.steps.lastOrNull()?.id
    rows.forEach { step ->
        androidx.compose.runtime.key(step.id) {
            val appear = remember { MutableTransitionState(step.id < animateFrom) }
            appear.targetState = true
            AnimatedVisibility(appear, enter = MikaTheme.motion.enter(TransformOrigin(0f, 0.5f)), exit = MikaTheme.motion.exit()) {
                StepRow(
                    step, first = step.id == 0, last = step.id == lastId,
                    open = expanded[step.id] == true,
                    onToggle = if (step.actions.isEmpty()) null else ({ expanded[step.id] = expanded[step.id] != true }),
                )
            }
        }
    }
}

@Composable
private fun StepRow(step: PlanStep, first: Boolean, last: Boolean, open: Boolean, onToggle: (() -> Unit)?) {
    val c = MikaTheme.colors
    val state = stepStateLabel(step.status)
    val focus = step.status == PlanStepStatus.Running || step.status == PlanStepStatus.Waiting ||
        step.status == PlanStepStatus.NeedsApproval
    val textColor = when (step.status) {
        PlanStepStatus.NeedsApproval, PlanStepStatus.Waiting -> c.warn
        PlanStepStatus.Failed -> c.ink
        PlanStepStatus.Pending, PlanStepStatus.Stopped -> c.ink3
        PlanStepStatus.Running -> c.ink
        PlanStepStatus.Done -> c.ink2
    }
    val rail = c.hair
    Row(
        Modifier.fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(
                onClickLabel = stringResource(if (open) R.string.collapse else R.string.chat_expand_steps), onClick = onToggle,
            ) else Modifier)
            .heightIn(min = 32.dp)
            .drawBehind {
                // The rail runs through every step; the first starts at its mark, the last ends there.
                val x = RailWidth.toPx() / 2
                val y = MarkCenter.toPx()
                drawLine(rail, Offset(x, if (first) y else 0f), Offset(x, if (last) y else size.height), strokeWidth = 1.5.dp.toPx())
            }
            .semantics(mergeDescendants = true) {
                stateDescription = state
                if (focus) liveRegion = LiveRegionMode.Polite
            }
            .testTag("live-plan-step-${step.id}"),
    ) {
        Box(Modifier.width(RailWidth).padding(top = MarkCenter - MarkSize / 2), contentAlignment = Alignment.TopCenter) {
            StepMark(step.status, Modifier.size(MarkSize))
        }
        Spacer(Modifier.width(MikaSpace.S8))
        Column(Modifier.weight(1f).padding(vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                step.tag?.let { t ->
                    Text(
                        t, Modifier.padding(top = 2.dp, end = 6.dp).background(c.accentSoft, MikaShapes.Tile).padding(horizontal = 5.dp),
                        style = MikaType.Hint, color = c.accent, maxLines = 1,
                    )
                }
                Text(
                    step.text, style = if (focus) MikaType.BodyStrong.copy(fontSize = MikaType.Caption.fontSize, lineHeight = MikaType.Caption.lineHeight)
                    else MikaType.Caption,
                    color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (step.status == PlanStepStatus.Failed && step.note.isNotBlank()) {
                Text(step.note, style = MikaType.Hint, color = c.bad, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (open) step.actions.forEach { a ->
                Text(
                    ChatCopy.actionLine(a), Modifier.padding(top = 2.dp),
                    style = MikaType.Hint, color = if (a.failed) c.bad else c.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun stepStateLabel(status: PlanStepStatus): String = stringResource(
    when (status) {
        PlanStepStatus.Pending -> R.string.plan_step_pending
        PlanStepStatus.Running -> R.string.plan_step_running
        PlanStepStatus.Waiting -> R.string.plan_step_waiting
        PlanStepStatus.NeedsApproval -> R.string.plan_step_needs_approval
        PlanStepStatus.Done -> R.string.plan_step_done
        PlanStepStatus.Failed -> R.string.step_failed
        PlanStepStatus.Stopped -> R.string.plan_step_stopped
    },
)

/**
 * A step's mark. The background disc hides the rail behind it. Done draws its check stroke from 0 to 1 on the
 * default spatial spring the moment the step finishes (already-finished steps show it whole).
 */
@Composable
fun StepMark(status: PlanStepStatus, modifier: Modifier = Modifier) {
    val c = MikaTheme.colors
    when (status) {
        PlanStepStatus.Running -> RingSpinner(modifier, c.accent, c.accentSoft, spinning = true)
        else -> {
            val draw = remember { Animatable(if (status == PlanStepStatus.Done) 1f else 0f) }
            val motion = MikaTheme.motion
            LaunchedEffect(status) {
                if (status == PlanStepStatus.Done && draw.value < 1f) draw.animateTo(1f, motion.spatialDefault())
                else if (status != PlanStepStatus.Done) draw.snapTo(0f)
            }
            val bg = c.bg
            Canvas(modifier) { drawMark(status, c, bg) { draw.value } }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMark(
    status: PlanStepStatus, c: MikaColors, bg: Color, progress: () -> Float,
) {
    val r = size.minDimension / 2
    drawCircle(bg, r)
    when (status) {
        PlanStepStatus.Pending, PlanStepStatus.Stopped -> drawCircle(c.ink3.copy(alpha = 0.45f), r * 0.32f)
        PlanStepStatus.Done -> {
            drawCircle(c.ok.copy(alpha = 0.16f), r)
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.67f)
                lineTo(size.width * 0.73f, size.height * 0.36f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * progress().coerceIn(0f, 1f), partial, true)
            drawPath(partial, c.ok, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        }
        PlanStepStatus.Failed -> {
            drawCircle(c.badSoft, r)
            val a = size.width * 0.34f
            val b = size.width * 0.66f
            val w = 1.8.dp.toPx()
            drawLine(c.bad, Offset(a, a), Offset(b, b), w, StrokeCap.Round)
            drawLine(c.bad, Offset(b, a), Offset(a, b), w, StrokeCap.Round)
        }
        PlanStepStatus.Waiting, PlanStepStatus.NeedsApproval -> {
            drawCircle(c.warnSoft, r)
            drawCircle(c.warnSolid, r * 0.38f)
        }
        PlanStepStatus.Running -> Unit
    }
}

/** The shared progress ring: a turning arc while working (still when animations are removed). */
@Composable
fun RingSpinner(modifier: Modifier, head: Color, track: Color, spinning: Boolean) {
    val phase = if (spinning) rememberMikaPhase(label = "plan-ring") else null
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2
        val box = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
        drawArc(head, (phase?.value ?: 0f) * 360f - 90f, 100f, false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/**
 * The plan's status ring, shared by the card header and the compact pill. Determinate (sweeps with
 * [LivePlan.progress] on the default spatial spring) when the plan's length is known, a turning arc otherwise;
 * amber and still when it waits on you, a check when done, a red mark when it failed.
 */
@Composable
fun PlanRing(plan: LivePlan, modifier: Modifier = Modifier) {
    val c = MikaTheme.colors
    when (plan.phase) {
        PlanPhase.Done -> StepMark(PlanStepStatus.Done, modifier)
        PlanPhase.Failed -> StepMark(PlanStepStatus.Failed, modifier)
        PlanPhase.Stopped -> StepMark(PlanStepStatus.Stopped, modifier)
        PlanPhase.Waiting, PlanPhase.NeedsApproval -> RingSpinner(modifier, c.warnSolid, c.warnSoft, spinning = false)
        PlanPhase.Running -> {
            val p = plan.progress
            if (p == null) RingSpinner(modifier, c.accent, c.accentSoft, spinning = true)
            else {
                val sweep by animateFloatAsState(p, MikaTheme.motion.spatialDefault(), label = "plan-progress")
                Canvas(modifier) {
                    val stroke = 2.dp.toPx()
                    val inset = stroke / 2
                    val box = Size(size.width - stroke, size.height - stroke)
                    drawArc(c.accentSoft, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
                    drawArc(c.accent, -90f, 360f * sweep, false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
    }
}

/**
 * The compact form: ring and the current step on one line; tap to open the full card. Used where only a pill
 * fits (the mini view's collapsed pill, the capsule).
 */
@Composable
fun LivePlanPill(plan: LivePlan, onExpand: (() -> Unit)?, modifier: Modifier = Modifier, textColor: Color = MikaTheme.colors.ink) {
    val c = MikaTheme.colors
    val now = plan.current?.text ?: plan.title
    val description = listOfNotNull(now, stringResource(R.string.pip_step, plan.stepNumber).takeIf { plan.stepNumber > 0 })
        .joinToString(", ")
    Row(
        modifier.heightIn(min = MikaSize.TouchTarget)
            .then(if (onExpand != null) Modifier.clip(MikaShapes.Pill).clickable(
                role = Role.Button, onClickLabel = stringResource(R.string.chat_expand_steps), onClick = onExpand,
            ) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag("live-plan-pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8),
    ) {
        PlanRing(plan, Modifier.size(16.dp))
        Text(now, style = MikaType.Caption, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val RailWidth = 20.dp
private val MarkSize = 16.dp
private val MarkCenter = 15.dp
private val FadeEdge = 28.dp
