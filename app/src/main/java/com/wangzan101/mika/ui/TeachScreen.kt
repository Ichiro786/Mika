package com.wangzan101.mika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import com.wangzan101.mika.R
import com.wangzan101.mika.core.teach.TeachPhase
import com.wangzan101.mika.core.teach.TeachStep
import com.wangzan101.mika.core.teach.teachGoalAccepted
import com.wangzan101.mika.core.teach.teachLine
import com.wangzan101.mika.teach.TeachNotice
import com.wangzan101.mika.teach.TeachRuntime
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.components.OrbitNote
import com.wangzan101.mika.ui.components.OrbitTextField
import com.wangzan101.mika.ui.components.OrbitFieldShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/**
 * Teach Orbit: the goal form, the live demonstration controls, step review and the saved draft.
 * Every phase uses the same frame: scrolling content on top, full-width actions pinned to the bottom.
 */
@Composable
fun TeachScreen(runtime: TeachRuntime, onBack: () -> Unit, onOpenDrafts: () -> Unit) {
    val ui by runtime.ui.collectAsState()
    val session = ui.session
    Column(Modifier.fillMaxSize().background(MikaTheme.colors.bg)) {
        TopBar(stringResource(R.string.teach_title), MikaIcons.Back, onBack, stringResource(R.string.back))
        Notice(ui.notice, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        when (session?.phase) {
            null, TeachPhase.Idle, TeachPhase.Ready -> GoalForm(runtime, ui.hint)
            TeachPhase.Recording, TeachPhase.Paused -> Live(runtime, session.phase, session.steps.size)
            TeachPhase.Interrupted -> Interrupted(runtime)
            TeachPhase.Reviewing, TeachPhase.Drafting -> Review(runtime, session.steps, session.phase == TeachPhase.Drafting)
            TeachPhase.Saved -> Saved(runtime, onOpenDrafts)
        }
    }
}

@Composable
private fun Notice(notice: TeachNotice?, modifier: Modifier = Modifier) {
    val text = when (notice) {
        TeachNotice.Busy -> stringResource(R.string.teach_busy)
        TeachNotice.NoAccessibility -> stringResource(R.string.teach_need_a11y)
        TeachNotice.NeedGoal -> stringResource(R.string.teach_need_goal)
        TeachNotice.SecretGoal -> stringResource(R.string.teach_secret_goal)
        TeachNotice.DraftFailed -> stringResource(R.string.teach_draft_failed)
        TeachNotice.NoSteps -> stringResource(R.string.teach_no_steps)
        TeachNotice.SecretNote -> stringResource(R.string.teach_sensitive_note)
        TeachNotice.NoVisibleStop -> stringResource(R.string.teach_no_visible)
        TeachNotice.WatchLost -> stringResource(R.string.teach_watch_lost)
        null -> return
    }
    val c = MikaTheme.colors
    Row(
        modifier.fillMaxWidth().background(c.warnSoft, OrbitFieldShape).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(MikaIcons.Exclaim, null, Modifier.padding(top = 1.dp).size(18.dp), tint = c.warn)
        Text(text, Modifier.weight(1f), style = MikaType.Caption, color = c.ink)
    }
}

/** Scrolling content with the actions pinned below it, clear of the keyboard and navigation bar. */
@Composable
private fun TeachFrame(
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        Column(
            modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = actions,
        )
    }
}

@Composable
private fun TeachHeading(title: String, body: String? = null) {
    Column {
        Text(title, style = MikaType.Title.copy(fontSize = 24.sp, lineHeight = 30.sp), color = MikaTheme.colors.ink, modifier = Modifier.semantics { heading() })
        if (body != null) Text(body, Modifier.padding(top = 6.dp), style = MikaType.Body, color = MikaTheme.colors.ink2)
    }
}

@Composable
private fun WideButton(text: String, onClick: () -> Unit, kind: ButtonKind = ButtonKind.Primary, enabled: Boolean = true, modifier: Modifier = Modifier) {
    MikaButton(text, onClick, modifier.fillMaxWidth(), kind = kind, size = ButtonSize.Large, enabled = enabled)
}

@Composable
private fun GoalForm(runtime: TeachRuntime, hint: String?) {
    var goal by remember { mutableStateOf(hint.orEmpty()) }
    var armed by remember { mutableStateOf(false) }
    var local by remember { mutableStateOf<TeachNotice?>(null) }
    LaunchedEffect(hint) { if (goal.isEmpty() && !hint.isNullOrEmpty()) goal = hint }
    val context = LocalContext.current
    TeachFrame(
        Modifier.testTag("teach-form"),
        actions = {
            if (!armed) {
                WideButton(stringResource(R.string.teach_continue), {
                    local = when {
                        goal.isBlank() -> TeachNotice.NeedGoal
                        !teachGoalAccepted(goal) -> TeachNotice.SecretGoal
                        else -> null
                    }
                    if (local == null) armed = true
                })
            } else {
                WideButton(stringResource(R.string.teach_start), {
                    local = runtime.start(goal)
                    if (local == null) {
                        armed = false
                        (context as? Activity)?.moveTaskToBack(true)
                    }
                }, modifier = Modifier.testTag("teach-start"))
                WideButton(stringResource(R.string.back), { armed = false }, kind = ButtonKind.Tonal)
            }
        },
    ) {
        Notice(local)
        if (!armed) {
            OrbitTextField(
                goal, { goal = it; local = null },
                stringResource(R.string.teach_goal_label),
                Modifier.testTag("teach-goal"),
                placeholder = stringResource(R.string.teach_goal_hint),
                singleLine = false,
            )
            OrbitNote(MikaIcons.Shield, stringResource(R.string.teach_disclosure))
        } else {
            Text(stringResource(R.string.teach_ready_body), style = MikaType.Body, color = MikaTheme.colors.ink2)
            MikaCard {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).background(MikaTheme.colors.fill, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(MikaIcons.Spark, null, Modifier.size(18.dp), tint = MikaTheme.colors.ink2)
                    }
                    Text(goal, Modifier.weight(1f), style = MikaType.BodyStrong, color = MikaTheme.colors.ink)
                }
            }
        }
    }
}

@Composable
private fun Live(runtime: TeachRuntime, phase: TeachPhase, steps: Int) {
    val context = LocalContext.current
    TeachFrame(actions = {
        if (phase == TeachPhase.Recording) WideButton(stringResource(R.string.teach_pause), runtime::pause, kind = ButtonKind.Tonal)
        else WideButton(stringResource(R.string.teach_resume), {
            if (runtime.resume() == null) (context as? Activity)?.moveTaskToBack(true)
        })
        WideButton(stringResource(R.string.teach_end), runtime::end)
        WideButton(stringResource(R.string.teach_discard), runtime::discard, kind = ButtonKind.Text)
    }) {
        TeachHeading(
            stringResource(if (phase == TeachPhase.Recording) R.string.teach_learning else R.string.teach_paused),
            stringResource(R.string.teach_notification),
        )
        StepCount(steps)
    }
}

/** The number of steps captured so far, as a large figure in a card. */
@Composable
private fun StepCount(steps: Int) {
    val c = MikaTheme.colors
    MikaCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp).background(c.accent, CircleShape))
            Text(steps.toString(), style = MikaType.LargeTitle, color = c.ink)
        }
    }
}

@Composable
private fun Interrupted(runtime: TeachRuntime) {
    val context = LocalContext.current
    TeachFrame(actions = {
        WideButton(stringResource(R.string.teach_resume), {
            if (runtime.resume() == null) (context as? Activity)?.moveTaskToBack(true)
        })
        WideButton(stringResource(R.string.teach_end), runtime::end, kind = ButtonKind.Tonal)
        WideButton(stringResource(R.string.teach_discard), runtime::discard, kind = ButtonKind.Text)
    }) {
        Text(stringResource(R.string.teach_interrupted), style = MikaType.Body, color = MikaTheme.colors.ink)
    }
}

@Composable
private fun Review(runtime: TeachRuntime, steps: List<TeachStep>, drafting: Boolean) {
    var confirm by remember { mutableStateOf(false) }
    TeachFrame(actions = {
        WideButton(
            stringResource(R.string.teach_generate), runtime::generate,
            enabled = steps.isNotEmpty() && !drafting, modifier = Modifier.testTag("teach-generate"),
        )
        if (confirm) {
            Text(stringResource(R.string.teach_discard_body), style = MikaType.Caption, color = MikaTheme.colors.warn)
            WideButton(stringResource(R.string.teach_discard), runtime::discard, kind = ButtonKind.Tonal)
        } else {
            WideButton(stringResource(R.string.teach_discard), { confirm = true }, kind = ButtonKind.Text)
        }
    }) {
        TeachHeading(stringResource(R.string.teach_review))
        if (steps.isEmpty()) Text(stringResource(R.string.teach_no_steps), style = MikaType.Body, color = MikaTheme.colors.ink2)
        steps.forEachIndexed { i, step -> StepEditor(runtime, step, i + 1) }
    }
}

@Composable
private fun StepEditor(runtime: TeachRuntime, step: TeachStep, number: Int) {
    var correction by remember(step.id, step.correction) { mutableStateOf(step.correction.orEmpty()) }
    var explanation by remember(step.id, step.explanation) { mutableStateOf(step.explanation.orEmpty()) }
    val c = MikaTheme.colors
    MikaCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(24.dp).background(c.fill, CircleShape), contentAlignment = Alignment.Center) {
                    Text(number.toString(), style = MikaType.Hint, color = c.ink2)
                }
                Text(teachLine(step), Modifier.weight(1f), style = MikaType.Body, color = c.ink)
            }
            if (step.insufficient) Text(stringResource(R.string.teach_insufficient_note), style = MikaType.Hint, color = c.ink3)
            if (step.sensitiveOmitted) Text(stringResource(R.string.teach_sensitive_note), style = MikaType.Hint, color = c.ink3)
            OrbitTextField(
                correction,
                {
                    correction = it
                    runtime.annotate(step.id, it, explanation)
                },
                stringResource(R.string.teach_correct),
                singleLine = false,
            )
            if (step.needsClarification) {
                OrbitTextField(
                    explanation,
                    { explanation = it; runtime.annotate(step.id, correction, it) },
                    stringResource(R.string.teach_clarify),
                    Modifier.testTag("teach-clarify"),
                    singleLine = false,
                )
            }
            Row {
                MikaButton(stringResource(R.string.teach_remove), { runtime.remove(step.id) }, kind = ButtonKind.Tonal, size = ButtonSize.Small)
            }
        }
    }
}

@Composable
private fun Saved(runtime: TeachRuntime, onOpenDrafts: () -> Unit) {
    TeachFrame(actions = {
        WideButton(stringResource(R.string.teach_open_drafts), onOpenDrafts)
        WideButton(stringResource(R.string.teach_discard), runtime::discard, kind = ButtonKind.Text)
    }) {
        Box(Modifier.size(52.dp).background(MikaTheme.colors.okSoft, CircleShape), contentAlignment = Alignment.Center) {
            Icon(MikaIcons.Check, null, Modifier.size(24.dp), tint = MikaTheme.colors.ok)
        }
        TeachHeading(stringResource(R.string.teach_draft_ready))
    }
}
