package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wangzan101.mika.R
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.personal.PersonalTask
import com.wangzan101.mika.core.run.RunPhase
import com.wangzan101.mika.core.run.RunStore
import com.wangzan101.mika.core.run.StoredRun
import com.wangzan101.mika.chat.ConversationStore
import com.wangzan101.mika.core.trigger.TriggerDefinition
import com.wangzan101.mika.core.trigger.TriggerSnapshot
import com.wangzan101.mika.personal.PersonalRuntime
import com.wangzan101.mika.personal.displayDetail
import com.wangzan101.mika.personal.displayResult
import com.wangzan101.mika.personal.displayText
import com.wangzan101.mika.personal.personalStopErrorText
import com.wangzan101.mika.task.TaskCenterState
import com.wangzan101.mika.task.TaskRun
import com.wangzan101.mika.task.TriggerRuntime
import com.wangzan101.mika.ui.components.BarIconButton
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.identity.MikaMark
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private data class LiveTask(val run: TaskRun, val state: TaskState, val recordId: String?)
private data class TaskItem(
    val personal: PersonalTask? = null,
    val live: LiveTask? = null,
    val stored: StoredRun? = null,
    val section: TaskSection,
) {
    val key: String get() = personal?.let { "personal-${it.id}" }
        ?: live?.let { "run-${it.run.key}" } ?: "stored-${stored!!.id.value}"
    val conversationId: String get() = personal?.conversationId ?: live?.run?.conversationId ?: stored!!.threadId
}

/** A view over existing task and schedule state; all execution stays with the original handlers. */
@Composable
internal fun TasksScreen(
    runtime: PersonalRuntime,
    taskState: TaskCenterState,
    runStore: RunStore,
    onOpenDrawer: () -> Unit,
    onOpenTask: (String) -> Unit,
    onPlans: (String?, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val c = MikaTheme.colors
    val errorInk = if (c.isDark) c.bad else Color(0xFFAB3028)
    val personal by runtime.state.collectAsState()
    val error by runtime.error.collectAsState()
    val runs by taskState.runs.collectAsState()
    val live = runs.map { run -> key(run.key) {
        val state by run.state.collectAsState()
        val recordId by run.runId.collectAsState()
        LiveTask(run, state, recordId)
    } }
    val personalConversations = personal.tasks.mapTo(mutableSetOf()) { it.conversationId }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedTask by rememberSaveable { mutableStateOf<String?>(null) }
    var replay by rememberSaveable { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val schedules by produceState<Result<TriggerSnapshot>?>(null, refresh) {
        value = null
        try {
            value = Result.success(withContext(Dispatchers.IO) { TriggerRuntime.snapshot(context) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            value = Result.failure(e)
        }
    }
    val history by produceState<Result<List<StoredRun>>?>(null, refresh, runs.map { it.key }) {
        value = null
        try {
            value = Result.success(withContext(Dispatchers.IO) {
                val conversations = ConversationStore.shared(context).list(includeArchived = true).mapTo(mutableSetOf()) { it.id }
                runStore.all().filter { it.threadId in conversations && (it.phase == RunPhase.Ended || it.phase == RunPhase.Interrupted) }.asReversed()
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            value = Result.failure(e)
        }
    }
    val liveRecords = live.mapNotNullTo(mutableSetOf()) { it.recordId }
    val tasks = personal.tasks.map { task ->
        val current = live.firstOrNull { it.run.conversationId == task.conversationId }
        TaskItem(personal = task, live = current, section = taskSection(task, current?.state))
    } + live.filter { it.run.conversationId !in personalConversations }.mapNotNull { current ->
        taskSection(current.state)?.let { TaskItem(live = current, section = it) }
    } + history?.getOrNull().orEmpty().filter {
        it.threadId !in personalConversations && (it.recordId == null || it.recordId !in liveRecords)
    }.map { TaskItem(stored = it, section = TaskSection.Resolved) }
    val plans = schedules?.getOrNull()?.triggers.orEmpty().filter { it.enabled }.sortedBy { it.nextAtMs }
    val visibleSections = if (tab == 0) listOf(
        TaskSection.Review, TaskSection.NeedsYou, TaskSection.Working, TaskSection.Waiting,
        TaskSection.Resolved,
    ) else listOf(TaskSection.Upcoming)
    val visibleTasks = tasks.filter { it.section in visibleSections }
    val historyError = personal.messages.lastOrNull { it.systemType == "history_unreadable" }
    val list = rememberLazyListState()

    Column(Modifier.fillMaxSize().background(c.bg).testTag("tasks-screen")) {
        TopBar(
            title = "", left = MikaIcons.Menu, onLeft = onOpenDrawer,
            leftLabel = stringResource(R.string.chat_menu),
            titleIcon = { MikaMark(Modifier.size(36.dp)) },
            right = { BarIconButton(MikaIcons.Alarm, stringResource(R.string.drawer_plans), { onPlans(null, false) }) },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = list, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 88.dp),
            ) {
                item("heading") {
                    Text(stringResource(R.string.tasks_title), style = MikaType.LargeTitle, color = c.ink,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.tasks_subtitle), style = MikaType.Body, color = c.ink2,
                        modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
                    TaskTabs(tab) { tab = it }
                }
                historyError?.let { message -> item("history-error") {
                    Text(message.displayText(context), Modifier.padding(top = 20.dp), style = MikaType.Body, color = errorInk)
                } }
                visibleSections.filter { it != TaskSection.Resolved }.forEach { section ->
                    val entries = visibleTasks.filter { it.section == section }
                    if (entries.isNotEmpty()) {
                        item("header-$section") { TaskSectionTitle(sectionLabel(section), entries.size) }
                        items(entries, key = { it.key }) { item ->
                            TaskRow(item) {
                                if (item.section == TaskSection.Review && item.live != null) onOpenTask(item.live.run.conversationId)
                                else if (item.personal != null) selectedTask = item.personal.id
                                else onOpenTask(item.conversationId)
                            }
                        }
                    }
                }
                if (schedules == null || history == null) item("schedules-loading") {
                    Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.loading), style = MikaType.Caption, color = c.ink2)
                    }
                }
                if (schedules?.isFailure == true || history?.isFailure == true) item("schedules-error") {
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.op_failed), Modifier.weight(1f), style = MikaType.Body, color = errorInk)
                        TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.retry)) }
                    }
                }
                if (plans.isNotEmpty()) {
                    item("header-scheduled") { TaskSectionTitle(stringResource(R.string.tasks_scheduled), plans.size) }
                    items(plans, key = { "plan-${it.id}" }) { plan -> ScheduleRow(plan) { onPlans(plan.id, false) } }
                }
                if (tab == 0) {
                    val resolved = visibleTasks.filter { it.section == TaskSection.Resolved }
                    if (resolved.isNotEmpty()) {
                        item("header-resolved") { TaskSectionTitle(stringResource(R.string.tasks_resolved), resolved.size) }
                        items(resolved, key = { it.key }) { item ->
                            TaskRow(item) {
                                if (item.personal != null) selectedTask = item.personal.id
                                else onOpenTask(item.conversationId)
                            }
                        }
                    }
                }
                if (visibleTasks.isEmpty() && plans.isEmpty() && schedules?.isSuccess == true && history?.isSuccess == true && historyError == null) {
                    item("empty") {
                        Text(stringResource(if (tab == 0) R.string.personal_no_tasks else R.string.tasks_empty_up_next),
                            Modifier.padding(top = 30.dp), style = MikaType.Body, color = c.ink2)
                    }
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(20.dp).size(56.dp)
                    .clip(RoundedCornerShape(20.dp)).background(c.accent)
                    .clickable(onClickLabel = stringResource(R.string.plans_add), role = Role.Button) { onPlans(null, true) },
                contentAlignment = Alignment.Center,
            ) { Icon(MikaIcons.Plus, null, Modifier.size(24.dp), tint = c.onAccent) }
        }
    }

    selectedTask?.let { id -> personal.tasks.firstOrNull { it.id == id } }?.let { task ->
        key(task.id) {
            val hasReplay by produceState(false, task.id) { value = runtime.replayFrames(task.conversationId).isNotEmpty() }
            PersonalTaskSheet(task, onDismiss = { selectedTask = null },
                onOpen = { selectedTask = null; onOpenTask(task.conversationId) },
                onStop = { runtime.stop(task.id) },
                onReplay = if (hasReplay) ({ selectedTask = null; replay = task.conversationId }) else null)
        }
    }
    replay?.let { id -> PersonalReplayDialog(runtime, id, live.any { it.run.conversationId == id && it.state !is TaskState.Terminal }, null) { replay = null } }
    error?.let { failure ->
        AlertDialog(onDismissRequest = runtime::clearError, text = { Text(personalStopErrorText(context, failure)) },
            confirmButton = { TextButton(onClick = runtime::clearError) { Text(stringResource(R.string.personal_ok)) } })
    }
}

@Composable
private fun TaskTabs(selected: Int, onSelect: (Int) -> Unit) {
    val c = MikaTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            listOf(R.string.tasks_active, R.string.tasks_up_next).forEachIndexed { index, label ->
                Column(Modifier.heightIn(min = 48.dp).selectable(selected == index, role = Role.Tab, onClick = { onSelect(index) }),
                    verticalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(label), style = MikaType.BodyStrong, color = if (selected == index) c.ink else c.ink3,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected == index) c.ink else c.bg))
                }
            }
        }
        HorizontalDivider(color = c.hair)
    }
}

@Composable
private fun sectionLabel(section: TaskSection): String = stringResource(when (section) {
    TaskSection.Review -> R.string.tasks_review
    TaskSection.NeedsYou -> R.string.personal_status_needs_you
    TaskSection.Working -> R.string.chat_working
    TaskSection.Waiting -> R.string.tasks_waiting
    TaskSection.Upcoming -> R.string.personal_status_queued
    TaskSection.Resolved -> R.string.tasks_resolved
})

@Composable
private fun TaskSectionTitle(label: String, count: Int) {
    Text("$label · $count", Modifier.padding(top = 28.dp, bottom = 8.dp).semantics { heading() },
        style = MikaType.Label, fontWeight = FontWeight.SemiBold, color = MikaTheme.colors.ink3)
}

@Composable
private fun TaskRow(item: TaskItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val c = MikaTheme.colors
    val state = item.live?.state
    val task = item.personal
    val title = task?.title ?: item.live?.run?.goal ?: item.stored?.userText ?: item.stored?.goal.orEmpty()
    val detail = when (state) {
        is TaskState.Running -> state.progress?.let(StatusCopy::progress) ?: state.narration ?: state.reason
        is TaskState.WaitingInput -> state.prompt
        is TaskState.WaitingTakeover -> state.message
        is TaskState.Held -> state.reason.why
        is TaskState.Failed -> state.reason
        else -> null
    }?.takeIf { it.isNotBlank() }
        ?: task?.takeIf { item.section == TaskSection.Resolved }?.displayResult(context)?.takeIf { it.isNotBlank() }
        ?: task?.displayDetail(context)?.takeIf { it.isNotBlank() }
        ?: item.stored?.reply?.takeIf { it.isNotBlank() }
    val progress = state?.let(::taskProgress)
    val attention = if (c.isDark) c.warn else Color(0xFF8C5000)
    val status = when {
        state == TaskState.Cancelled -> stringResource(R.string.personal_status_cancelled)
        state is TaskState.Failed -> stringResource(R.string.personal_status_incomplete)
        state is TaskState.Succeeded -> stringResource(R.string.personal_status_completed)
        item.stored?.phase == RunPhase.Interrupted || item.stored?.runSucceeded == false -> stringResource(R.string.personal_status_incomplete)
        item.stored?.runSucceeded == true -> stringResource(R.string.personal_status_completed)
        task != null && item.live == null -> personalStatus(task)
        else -> sectionLabel(item.section)
    }
    Row(Modifier.fillMaxWidth().clip(MikaShapes.Card)
        .clickable(onClickLabel = stringResource(R.string.personal_task_details), role = Role.Button, onClick = onClick)
        .padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 3.dp).size(19.dp).border(1.dp, c.hair, RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f)) {
            Text(title, style = MikaType.BodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(status, Modifier.padding(top = 4.dp), style = MikaType.Caption,
                color = if (item.section == TaskSection.Review || item.section == TaskSection.NeedsYou) attention else c.ink2)
            if (!detail.isNullOrBlank() && detail != title && detail != status) Text(detail,
                Modifier.padding(top = 3.dp), style = MikaType.Caption, color = c.ink2, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (progress != null) LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp),
                color = c.accent, trackColor = c.fill)
        }
        if (item.section == TaskSection.Review) Text(stringResource(R.string.teach_review), style = MikaType.Caption, color = attention)
        Icon(MikaIcons.Chevron, null, Modifier.size(18.dp), tint = c.ink3)
    }
    HorizontalDivider(color = c.hair)
}

@Composable
private fun ScheduleRow(plan: TriggerDefinition, onClick: () -> Unit) {
    val c = MikaTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale) }
    val time = remember(plan.nextAtMs, format) { format.format(Instant.ofEpochMilli(plan.nextAtMs).atZone(ZoneId.systemDefault())) }
    Row(Modifier.fillMaxWidth().clip(MikaShapes.Card).clickable(role = Role.Button, onClick = onClick).padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 3.dp).size(19.dp).border(1.dp, c.hair, RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f)) {
            Text(plan.reminderText ?: plan.goal, style = MikaType.BodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(time, Modifier.padding(top = 4.dp), style = MikaType.Caption, color = c.ink2)
        }
        Icon(MikaIcons.Chevron, null, Modifier.size(18.dp), tint = c.ink3)
    }
    HorizontalDivider(color = c.hair)
}
