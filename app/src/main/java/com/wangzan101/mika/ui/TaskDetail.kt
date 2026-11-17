package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.components.BarIconButton
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.components.StepItem
import com.wangzan101.mika.ui.components.StepState
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/** What a task's detail page shows; built by the Tasks page from the runtime's own state, nothing invented. */
internal data class TaskDetailUi(
    val title: String,
    val status: String,
    val group: TaskGroup,
    /** Finished by Orbit or marked done by the user. */
    val checked: Boolean,
    /** The delegated goal, when it says more than the title. */
    val goal: String?,
    /** What it is doing or waiting on right now. */
    val progressText: String?,
    /** Done over total, only when the runtime reports both. */
    val progress: Float?,
    val result: String?,
    /** Still going: the last step is the current one and Stop is offered. */
    val live: Boolean,
)

/** At most this many steps are listed here; the full timeline is one tap away. */
internal const val TASK_DETAIL_STEPS = 40

/** The newest [TASK_DETAIL_STEPS] steps, oldest first, and how many earlier ones were left out. */
internal fun <T> recentSteps(steps: List<T>): Pair<List<T>, Int> =
    steps.takeLast(TASK_DETAIL_STEPS) to (steps.size - TASK_DETAIL_STEPS).coerceAtLeast(0)

/**
 * One task in full: its title and state, progress, the steps Orbit took (from the run record), the result,
 * and the way to its chat. Stop, replay, mark done and delete are the same actions the list and the old sheet had.
 */
@Composable
internal fun TaskDetail(
    ui: TaskDetailUi,
    /** Null while the run record is being read. */
    steps: List<TimelineStep>?,
    reduceMotion: Boolean,
    bottomInset: Dp,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenRun: (() -> Unit)?,
    onStop: (() -> Unit)?,
    onReplay: (() -> Unit)?,
    onMarkDone: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val c = MikaTheme.colors
    val attention = if (c.isDark) c.warn else Color(0xFF8C5000)
    Column(Modifier.fillMaxSize().background(c.bg).testTag("task-detail")) {
        TopBar(
            title = "", left = MikaIcons.Back, onLeft = onBack, leftLabel = stringResource(R.string.back),
            right = { BarIconButton(MikaIcons.Trash, stringResource(R.string.tasks_delete), onDelete) },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                TaskCircle(
                    checked = ui.checked, running = ui.group == TaskGroup.Running, progress = ui.progress,
                    attention = ui.group == TaskGroup.NeedsYou, reduceMotion = reduceMotion,
                    label = stringResource(if (ui.live) R.string.tasks_mark_done_live else R.string.tasks_mark_done),
                    onClick = onMarkDone,
                )
                Column(Modifier.weight(1f).padding(top = 9.dp)) {
                    Text(ui.title, Modifier.semantics { heading() }, style = MikaType.Title.copy(fontSize = MikaType.LargeTitle.fontSize * 0.69f, lineHeight = MikaType.LargeTitle.lineHeight * 0.72f), color = c.ink)
                    Text(ui.status, Modifier.padding(top = 6.dp), style = MikaType.Body,
                        color = if (ui.group == TaskGroup.NeedsYou) attention else c.ink2)
                }
            }
            if (ui.progress != null) LinearProgressIndicator(
                progress = { ui.progress }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(4.dp),
                color = c.accent, trackColor = c.fill,
            )
            ui.progressText?.takeIf { it != ui.status && it != ui.title }?.let { Section(stringResource(R.string.personal_progress)) { BodyText(it) } }
            ui.goal?.let { Section(stringResource(R.string.personal_goal)) { BodyText(it) } }
            Section(stringResource(R.string.task_detail_steps)) {
                when {
                    steps == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.loading), style = MikaType.Caption, color = c.ink2)
                    }
                    steps.isEmpty() -> Text(stringResource(R.string.task_detail_no_steps), style = MikaType.Body, color = c.ink2)
                    else -> {
                        val (recent, earlier) = recentSteps(steps)
                        if (earlier > 0) Text(stringResource(R.string.task_detail_earlier_steps, earlier), Modifier.padding(bottom = 6.dp), style = MikaType.Caption, color = c.ink3)
                        recent.forEachIndexed { i, step ->
                            val last = i == recent.lastIndex
                            StepItem(
                                state = if (last && ui.live) (if (ui.group == TaskGroup.NeedsYou) StepState.Paused else StepState.Current) else StepState.Done,
                                first = i == 0, last = last,
                            ) {
                                val line = step.tool ?: step.said
                                Text(line.ifBlank { stringResource(R.string.chat_working) }, style = MikaType.Body, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val under = listOfNotNull(step.detail, step.result).filter { it.isNotBlank() }.joinToString(" · ")
                                if (under.isNotEmpty()) Text(under, style = MikaType.Caption, color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (step.tool != null && step.said.isNotBlank()) Text(step.said, Modifier.padding(bottom = 8.dp), style = MikaType.Caption, color = c.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                else androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
            ui.result?.let { result ->
                Section(stringResource(R.string.personal_result)) {
                    MikaCard { MarkdownText(result, MikaType.Body.copy(color = c.ink), Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomInset + 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MikaButton(
                stringResource(R.string.task_detail_open_chat), onOpenChat,
                Modifier.fillMaxWidth().testTag("task-open-chat"), kind = ButtonKind.Primary,
            )
            val secondary = listOfNotNull(
                onOpenRun?.let { stringResource(R.string.chat_view_process) to it },
                onReplay?.let { stringResource(R.string.personal_view_replay) to it },
                onStop?.let { stringResource(R.string.chat_stop) to it },
            )
            if (secondary.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                secondary.forEach { (label, action) ->
                    MikaButton(label, action, Modifier.weight(1f), kind = ButtonKind.Tonal, size = ButtonSize.Medium)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title, Modifier.padding(top = 26.dp, bottom = 8.dp).semantics { heading() },
        style = MikaType.Label, color = MikaTheme.colors.ink3,
    )
    content()
}

@Composable
private fun BodyText(text: String) = Text(text, style = MikaType.Body, color = MikaTheme.colors.ink)
