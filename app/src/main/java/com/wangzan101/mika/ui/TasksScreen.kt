package com.wangzan101.mika.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.TextStyle
import com.wangzan101.mika.core.assembly.knownTools
import com.wangzan101.mika.task.TaskService
import com.wangzan101.mika.ui.components.floatShadow
import com.wangzan101.mika.ui.theme.MikaMotion
import com.wangzan101.mika.ui.theme.MikaSize
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wangzan101.mika.task.ExactAlarmGuide
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.EmptyStateCard
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.StateCard
import com.wangzan101.mika.ui.components.TileTone
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
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.identity.MikaPresenceAvatar
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    /** Every name this task goes by, for the user's done and removed marks (see [taskMarkIds]). */
    val ids: List<String> get() = taskMarkIds(
        personalId = personal?.id, liveKey = live?.run?.key,
        recordId = live?.recordId ?: stored?.recordId, storedId = stored?.id?.value,
    )
    /** The run record that holds this task's steps, when one is known without reading the chat. */
    val recordId: String? get() = live?.recordId ?: stored?.let { it.recordId ?: it.id.value }
    val title: String get() = personal?.title ?: live?.run?.goal ?: stored?.userText ?: stored?.goal.orEmpty()
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
    /** Opens the full run timeline ("See how it went") from a task's detail. */
    onOpenRun: (String) -> Unit = {},
    onOpenOrbitHub: () -> Unit = {},
    presence: com.wangzan101.mika.ui.identity.PresenceState = com.wangzan101.mika.ui.identity.PresenceState.Idle,
    bottomInset: Dp = 0.dp,
) {
    val context = LocalContext.current
    val c = MikaTheme.colors
    val errorInk = if (c.isDark) c.bad else Color(0xFFAB3028)
    val personal by runtime.state.collectAsState()
    val error by runtime.error.collectAsState()
    val runs by taskState.runs.collectAsState()
    val live = runs.map { run -> key(run.key) {
        val displayStates = remember(run) { run.state.map(::taskDisplayState).distinctUntilChanged() }
        val state by displayStates.collectAsState(initial = taskDisplayState(run.state.value))
        val recordId by run.runId.collectAsState()
        // A run counts as a task once Orbit actually works on it (uses a tool, or stops to ask); a plain chat reply never does.
        var worked by remember(run.key) { mutableStateOf(false) }
        if (!worked && startedWork(state)) worked = true
        LiveTask(run, state, recordId).takeIf { worked || state.isQueued() }
    } }.filterNotNull()
    val personalConversations = personal.tasks.mapTo(mutableSetOf()) { it.conversationId }
    val reduceMotion = MikaTheme.reduceMotion
    var replay by rememberSaveable { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var creatingPlan by rememberSaveable { mutableStateOf<String?>(null) }
    // Create right here, over Tasks: no detour through the schedules page, and Cancel stays on Tasks.
    val startPlan: () -> Unit = {
        creatingPlan = runCatching { ConversationStore.shared(context).newConversation() }.getOrNull()
    }
    // Exact alarms can be switched off in system settings; reread on return so the notice goes away.
    var exact by remember { mutableStateOf(TriggerRuntime.canScheduleExactly(context)) }
    LifecycleResumeEffect(Unit) {
        refresh++
        exact = TriggerRuntime.canScheduleExactly(context)
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
                val store = ConversationStore.shared(context)
                val conversations = store.list(includeArchived = true).mapTo(mutableSetOf()) { it.id }
                // Only runs where Orbit used a tool are tasks; chat replies stay in the chat.
                val worked = HashMap<String, Set<String>>()
                fun workedRuns(thread: String) = worked.getOrPut(thread) {
                    runCatching { store.turns(thread) }.getOrDefault(emptyList())
                        .filter { it.groups.isNotEmpty() }.mapNotNullTo(mutableSetOf()) { it.runId }
                }
                runStore.all().filter { r ->
                    r.threadId in conversations && (r.phase == RunPhase.Ended || r.phase == RunPhase.Interrupted) &&
                        workedRuns(r.threadId).let { ids -> r.id.value in ids || r.recordId in ids }
                }.asReversed()
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
    val (plans, stoppedPlans) = schedules?.getOrNull()?.triggers.orEmpty().sortedBy { it.nextAtMs }.partition { it.enabled }
    // What the user marked done or removed here. A change shows at once and sticks only once its Undo has passed.
    val marks = remember { TaskMarks.shared(context) }
    val marked by marks.state.collectAsState()
    var pendingDone by remember { mutableStateOf(emptySet<String>()) }
    var pendingDeleted by remember { mutableStateOf(emptySet<String>()) }
    val shown = tasks.filter { it.key !in pendingDeleted && !taskMarked(it.ids, marked.deleted) }
    fun markedDone(item: TaskItem) = item.key in pendingDone || taskMarked(item.ids, marked.done)
    val grouped = shown.groupBy { taskGroup(it.section, markedDone(it)) }
    val historyError = personal.messages.lastOrNull { it.systemType == "history_unreadable" }
    val list = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val latestTasks by rememberUpdatedState(tasks)
    var doneOpen by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }

    /** Stops what is still going for [item]: a delegated task through its runtime, a plain run by its key. */
    fun stop(item: TaskItem) {
        val going = item.live?.takeIf { it.state !is TaskState.Terminal }
        if (item.personal != null) { if (going != null || !item.personal.terminal) runtime.stop(item.personal.id) }
        else if (going != null) TaskService.cancel(context, going.run.key)
    }

    /**
     * Shows the change at once and offers Undo. Only when the bar goes away without Undo does it stick: the mark
     * is saved and a task that is still going is stopped. Leaving the page while the bar is up counts as not undoing.
     */
    fun withUndo(item: TaskItem, message: String, pend: (Boolean) -> Unit, save: (List<String>) -> Unit) {
        pend(true)
        // A second change does not wait behind the first bar: the first one is settled now.
        snackbar.currentSnackbarData?.dismiss()
        scope.launch {
            var undone = false
            try {
                undone = snackbar.showSnackbar(message, context.getString(R.string.undo), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed
            } finally {
                if (!undone) {
                    val now = latestTasks.firstOrNull { it.key == item.key } ?: item
                    save((item.ids + now.ids).distinct())
                    stop(now)
                }
                pend(false)
            }
        }
    }
    fun markDone(item: TaskItem) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val going = item.section != TaskSection.Resolved
        withUndo(item, context.getString(if (going) R.string.tasks_marked_done_live else R.string.tasks_marked_done),
            { on -> pendingDone = if (on) pendingDone + item.key else pendingDone - item.key }, marks::markDone)
    }
    fun delete(item: TaskItem) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (detail == item.key) detail = null
        withUndo(item, context.getString(R.string.tasks_deleted),
            { on -> pendingDeleted = if (on) pendingDeleted + item.key else pendingDeleted - item.key }, marks::delete)
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
    Column(Modifier.fillMaxSize().testTag("tasks-screen")) {
        TopBar(
            title = "", left = MikaIcons.Menu, onLeft = onOpenDrawer,
            leftLabel = stringResource(R.string.chat_menu),
            titleIcon = { OrbitHubButton(presence, onOpenOrbitHub) },
            centerTitle = true,
            barHeight = orbitHeaderBarHeight(),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = list, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 26.dp, bottom = bottomInset + 88.dp),
            ) {
                item("heading") {
                    val title = stringResource(R.string.tasks_title)
                    val styled = buildAnnotatedString {
                        if (title.endsWith('.')) {
                            append(title.dropLast(1))
                            pushStyle(SpanStyle(color = c.accent))
                            append('.')
                            pop()
                        } else append(title)
                    }
                    OneLineTitle(styled, color = c.ink, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.tasks_subtitle), style = MikaType.Body, color = c.ink2,
                        modifier = Modifier.padding(top = 6.dp))
                }
                historyError?.let { message -> item("history-error") {
                    Text(message.displayText(context), Modifier.padding(top = 20.dp), style = MikaType.Body, color = errorInk)
                } }
                fun rows(entries: List<TaskItem>, group: TaskGroup) = items(entries, key = { it.key }) { item ->
                    TaskEntry(
                        item, group, markedDone(item), reduceMotion,
                        onOpen = { detail = item.key },
                        onMarkDone = { markDone(item) },
                        onDelete = { delete(item) },
                    )
                }
                listOf(
                    TaskGroup.Running to R.string.personal_status_running,
                    TaskGroup.NeedsYou to R.string.personal_status_needs_you,
                ).forEach { (group, label) ->
                    val entries = grouped[group].orEmpty()
                    if (entries.isNotEmpty()) {
                        item("header-$group") { TaskSectionTitle(stringResource(label), entries.size) }
                        rows(entries, group)
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
                if (!exact && (plans.isNotEmpty() || stoppedPlans.isNotEmpty())) item("exact-alarm") {
                    StateCard(
                        MikaIcons.Alarm, ExactAlarmGuide.TITLE, stringResource(R.string.plans_alarm_body),
                        tone = TileTone.Warn, modifier = Modifier.padding(top = 20.dp),
                    ) {
                        MikaButton(stringResource(R.string.turn_on_go), {
                            runCatching { context.startActivity(TriggerRuntime.exactAlarmSettings(context)) }
                        }, kind = ButtonKind.Primary, size = ButtonSize.Small)
                    }
                }
                // Up next: what is queued to start, then what is scheduled to run on its own.
                val queued = grouped[TaskGroup.UpNext].orEmpty()
                if (queued.isNotEmpty() || plans.isNotEmpty()) {
                    item("header-up-next") { TaskSectionTitle(stringResource(R.string.tasks_up_next), queued.size + plans.size) }
                    rows(queued, TaskGroup.UpNext)
                    items(plans, key = { "plan-${it.id}" }) { plan -> ScheduleRow(plan) { onPlans(plan.id, false) } }
                }
                // Schedules that are switched off live here too, so nothing needs a separate schedules page to find.
                if (stoppedPlans.isNotEmpty()) {
                    item("header-stopped") { TaskSectionTitle(stringResource(R.string.chat_paused), stoppedPlans.size) }
                    items(stoppedPlans, key = { "stopped-${it.id}" }) { plan -> ScheduleRow(plan, stopped = true) { onPlans(plan.id, false) } }
                }
                // Done starts folded away: finished work is one tap from here, not in the way.
                val done = grouped[TaskGroup.Done].orEmpty()
                if (done.isNotEmpty()) {
                    item("header-done") { DoneHeader(done.size, doneOpen, reduceMotion) { doneOpen = !doneOpen } }
                    if (doneOpen) rows(done, TaskGroup.Done)
                }
                if (shown.isEmpty() && plans.isEmpty() && stoppedPlans.isEmpty() && schedules?.isSuccess == true && history?.isSuccess == true && historyError == null) {
                    item("empty") {
                        EmptyStateCard(
                            stringResource(R.string.personal_no_tasks),
                            Modifier.padding(top = 30.dp).testTag("tasks-empty"),
                            hint = stringResource(R.string.tasks_empty_hint),
                            icon = MikaIcons.Clock,
                            actionLabel = stringResource(R.string.plans_add),
                            onAction = startPlan,
                        )
                    }
                }
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = bottomInset + 20.dp).size(56.dp)
                    .clip(RoundedCornerShape(20.dp)).background(c.accent)
                    .clickable(onClickLabel = stringResource(R.string.plans_add), role = Role.Button, onClick = startPlan),
                contentAlignment = Alignment.Center,
            ) { Icon(MikaIcons.Plus, null, Modifier.size(24.dp), tint = c.onAccent) }
        }
    }

    // A task's detail slides over the list; Back returns to it.
    val open = detail?.let { key -> tasks.firstOrNull { it.key == key } }
    LaunchedEffect(detail, open == null) { if (detail != null && open == null) detail = null }
    AnimatedVisibility(
        open != null,
        enter = if (reduceMotion) EnterTransition.None else slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 380f)) { it / 3 } + fadeIn(tween(180)),
        exit = if (reduceMotion) ExitTransition.None else slideOutHorizontally(tween(180)) { it / 3 } + fadeOut(tween(160)),
    ) {
        // Keep showing the task while the page slides away after it is closed.
        var held by remember { mutableStateOf(open) }
        if (open != null) held = open
        held?.let { item ->
            BackHandler { detail = null }
            val state = item.live?.state
            val group = taskGroup(item.section, markedDone(item))
            // The run record with this task's steps: known for a live or stored run, read from the chat's last turn otherwise.
            val runId by produceState(item.recordId, item.key, item.recordId) {
                value = item.recordId ?: withContext(Dispatchers.IO) {
                    runCatching { ConversationStore.shared(context).turns(item.conversationId).lastOrNull { it.runId != null }?.runId }.getOrNull()
                }
            }
            // Steps are reread when the run moves to a new step or ends, not on a timer.
            val stepNow = (state as? TaskState.Running)?.step
            val steps by produceState<List<TimelineStep>?>(null, runId, stepNow, state is TaskState.Terminal) {
                val id = runId
                value = if (id == null) emptyList() else withContext(Dispatchers.IO) {
                    runCatching {
                        RunTimelineViewModel.steps(com.wangzan101.mika.record.RunStore(context).events(id), ToolLabels(knownTools())::label)
                    }.getOrDefault(emptyList())
                }
            }
            val hasReplay by produceState(false, item.key) {
                value = item.personal != null && runtime.replayFrames(item.conversationId).isNotEmpty()
            }
            val going = state != null && state !is TaskState.Terminal || (item.personal != null && item.live == null && !item.personal.terminal)
            TaskDetail(
                ui = TaskDetailUi(
                    title = item.title,
                    status = taskStatusLabel(item, markedDone(item)),
                    group = group,
                    checked = taskChecked(item, markedDone(item)),
                    goal = item.personal?.goal?.takeIf { it.isNotBlank() && it != item.title },
                    progressText = taskDetailLine(item, context),
                    progress = state?.let(::taskProgress),
                    result = taskResult(item, context),
                    live = going,
                ),
                steps = steps,
                reduceMotion = reduceMotion,
                bottomInset = bottomInset,
                onBack = { detail = null },
                onOpenChat = { detail = null; onOpenTask(item.conversationId) },
                onOpenRun = runId?.let { id -> { onOpenRun(id) } },
                onStop = if (going) ({ stop(item) }) else null,
                onReplay = if (hasReplay) ({ replay = item.conversationId }) else null,
                onMarkDone = if (!taskChecked(item, markedDone(item))) ({ detail = null; markDone(item) }) else null,
                onDelete = { delete(item) },
            )
        }
    }

    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = bottomInset + 12.dp)) { data ->
        UndoBar(data)
    }
    }

    creatingPlan?.let { threadId ->
        PlanEditorDialog(
            original = null,
            newThreadId = threadId,
            onDismiss = { creatingPlan = null },
            onSave = { plan ->
                runCatching { TriggerRuntime.schedule(context, plan) }
                    .onSuccess { creatingPlan = null; refresh++ }
                    .onFailure {
                        android.widget.Toast.makeText(context, it.message ?: context.getString(R.string.op_failed), android.widget.Toast.LENGTH_SHORT).show()
                    }
            },
        )
    }

    replay?.let { id -> PersonalReplayDialog(runtime, id, live.any { it.run.conversationId == id && it.state !is TaskState.Terminal }, null) { replay = null } }
    error?.let { failure ->
        AlertDialog(onDismissRequest = runtime::clearError, text = { Text(personalStopErrorText(context, failure)) },
            confirmButton = { TextButton(onClick = runtime::clearError) { Text(stringResource(R.string.personal_ok)) } })
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
private fun sectionTitleText(label: String, count: Int): Pair<String, TextStyle> {
    val locale = LocalConfiguration.current.locales[0]
    val latin = locale.language in setOf("en", "es", "in", "pt")
    return "${label.uppercase(locale)} · $count" to MikaType.Hint.copy(letterSpacing = if (latin) 0.8.sp else 0.sp)
}

@Composable
private fun TaskSectionTitle(label: String, count: Int) {
    val (text, style) = sectionTitleText(label, count)
    Text(text, Modifier.padding(top = 28.dp, bottom = 4.dp).semantics { heading() },
        style = style, fontWeight = FontWeight.SemiBold, color = MikaTheme.colors.ink3)
}

/** The Done group's title is also its switch: folded away until tapped. */
@Composable
private fun DoneHeader(count: Int, open: Boolean, reduceMotion: Boolean, onToggle: () -> Unit) {
    val c = MikaTheme.colors
    val (text, style) = sectionTitleText(stringResource(R.string.done), count)
    val turn by animateFloatAsState(if (open) 90f else 0f, MikaMotion.spatial(reduceMotion), label = "done-chevron")
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Tile)
            .clickable(onClickLabel = stringResource(if (open) R.string.hide else R.string.show), role = Role.Button, onClick = onToggle)
            .semantics { heading() }.testTag("tasks-done-header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = style, fontWeight = FontWeight.SemiBold, color = c.ink3)
        Icon(MikaIcons.Chevron, null, Modifier.size(18.dp).graphicsLayer { rotationZ = turn }, tint = c.ink3)
    }
}

/** Done by Orbit (it finished the work) or marked done by the user: the circle is filled with a check. */
private fun taskChecked(item: TaskItem, markedDone: Boolean): Boolean = markedDone ||
    item.live?.state is TaskState.Succeeded || (item.live == null && (item.stored?.runSucceeded == true || item.personal?.status == "completed"))

@Composable
private fun taskStatusLabel(item: TaskItem, markedDone: Boolean): String {
    val state = item.live?.state
    val task = item.personal
    return when {
        markedDone -> stringResource(R.string.done)
        state == TaskState.Cancelled -> stringResource(R.string.personal_status_cancelled)
        state is TaskState.Failed -> stringResource(R.string.personal_status_incomplete)
        state is TaskState.Succeeded -> stringResource(R.string.personal_status_completed)
        item.stored?.phase == RunPhase.Interrupted || item.stored?.runSucceeded == false -> stringResource(R.string.personal_status_incomplete)
        item.stored?.runSucceeded == true -> stringResource(R.string.personal_status_completed)
        task != null && item.live == null -> personalStatus(task)
        else -> sectionLabel(item.section)
    }
}

/** What the task is doing or waiting on right now, in the runtime's own words. */
private fun taskDetailLine(item: TaskItem, context: android.content.Context): String? = when (val state = item.live?.state) {
    is TaskState.Running -> state.progress?.let(StatusCopy::progress) ?: state.narration ?: state.reason
    is TaskState.WaitingInput -> state.prompt
    is TaskState.WaitingTakeover -> state.message
    is TaskState.Held -> state.reason.why
    is TaskState.Failed -> state.reason
    else -> null
}?.takeIf { it.isNotBlank() } ?: item.personal?.takeIf { !it.terminal }?.displayDetail(context)?.takeIf { it.isNotBlank() }

/** What came out of the task once it ended. */
private fun taskResult(item: TaskItem, context: android.content.Context): String? =
    item.personal?.displayResult(context)?.takeIf { it.isNotBlank() } ?: item.stored?.reply?.takeIf { it.isNotBlank() }

/**
 * One task: tap the row for its detail, tap the circle to mark it done, swipe towards the start to delete it
 * (a red strip with a bin shows underneath). TalkBack gets both as actions on the row.
 */
@Composable
private fun TaskEntry(
    item: TaskItem, group: TaskGroup, markedDone: Boolean, reduceMotion: Boolean,
    onOpen: () -> Unit, onMarkDone: () -> Unit, onDelete: () -> Unit,
) {
    val c = MikaTheme.colors
    val deleteLabel = stringResource(R.string.tasks_delete)
    val currentDelete by rememberUpdatedState(onDelete)
    val dismiss = rememberSwipeToDismissBoxState(
        // Swiped all the way: the list takes the row out; if the delete is undone it comes back in place.
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) currentDelete()
            value == SwipeToDismissBoxValue.EndToStart
        },
    )
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (dismiss.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    Modifier.fillMaxSize().padding(vertical = 4.dp).clip(MikaShapes.Card).background(c.bad)
                        .padding(horizontal = 22.dp).testTag("task-delete-swipe"),
                    contentAlignment = Alignment.CenterEnd,
                ) { Icon(MikaIcons.Trash, null, Modifier.size(22.dp), tint = if (c.isDark) Color(0xFF1F0503) else Color.White) }
            }
        },
    ) {
        TaskRow(item, group, markedDone, reduceMotion, deleteLabel, onOpen, onMarkDone, onDelete)
    }
}

@Composable
private fun TaskRow(
    item: TaskItem, group: TaskGroup, markedDone: Boolean, reduceMotion: Boolean, deleteLabel: String,
    onOpen: () -> Unit, onMarkDone: () -> Unit, onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val c = MikaTheme.colors
    val state = item.live?.state
    val title = item.title
    val checked = taskChecked(item, markedDone)
    val status = taskStatusLabel(item, markedDone)
    val detail = taskDetailLine(item, context)
        ?: taskResult(item, context)?.takeIf { group == TaskGroup.Done }
    val progress = state?.let(::taskProgress)
    val attention = if (c.isDark) c.warn else Color(0xFF8C5000)
    val going = item.section != TaskSection.Resolved
    val markLabel = stringResource(if (going) R.string.tasks_mark_done_live else R.string.tasks_mark_done)
    Column(Modifier.fillMaxWidth().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().clip(MikaShapes.Card)
                .clickable(onClickLabel = stringResource(R.string.personal_task_details), role = Role.Button, onClick = onOpen)
                .semantics {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(markLabel) { onMarkDone(); true }.takeIf { !checked },
                        CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                    )
                }
                .padding(top = 4.dp, bottom = 12.dp).testTag("task-row"),
            verticalAlignment = Alignment.Top,
        ) {
            TaskCircle(
                checked = checked, running = group == TaskGroup.Running, progress = progress,
                attention = group == TaskGroup.NeedsYou, reduceMotion = reduceMotion,
                label = markLabel, onClick = onMarkDone.takeIf { !checked },
            )
            Column(Modifier.weight(1f).padding(top = 12.dp, end = 8.dp)) {
                // Two lines, then an ellipsis: a long request never runs on or cuts off mid-word at the edge.
                Text(title, style = MikaType.BodyStrong, color = if (checked) c.ink2 else c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(status, Modifier.padding(top = 4.dp), style = MikaType.Caption,
                    color = if (group == TaskGroup.NeedsYou) attention else c.ink2)
                if (!detail.isNullOrBlank() && detail != title && detail != status) Text(detail,
                    Modifier.padding(top = 3.dp), style = MikaType.Caption, color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp),
                    color = c.accent, trackColor = c.fill)
            }
            if (item.section == TaskSection.Review) Text(stringResource(R.string.teach_review), Modifier.padding(top = 14.dp, end = 4.dp), style = MikaType.Caption, color = attention)
            Icon(MikaIcons.Chevron, null, Modifier.padding(top = 15.dp).size(18.dp), tint = c.ink3)
        }
        HorizontalDivider(color = c.hair)
    }
}

/**
 * The circle in front of a task, a 48 dp target around a 24 dp mark. Not done: a ring (warm when the task needs
 * the user; a turning arc or the real progress while it runs). Done: the ring fills and a check draws itself with
 * a small spring; with animations off it is simply there. Tapping an unfinished one marks the task done.
 */
@Composable
internal fun TaskCircle(
    checked: Boolean, running: Boolean, progress: Float?, attention: Boolean, reduceMotion: Boolean,
    label: String, onClick: (() -> Unit)?,
) {
    val c = MikaTheme.colors
    val fill by animateFloatAsState(
        if (checked) 1f else 0f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.55f, stiffness = 420f), label = "task-check",
    )
    val turning = running && !checked && progress == null && !reduceMotion
    val angle = if (turning) rememberInfiniteTransition(label = "task-arc").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "task-arc-angle",
    ) else null
    Box(
        Modifier.size(MikaSize.TouchTarget)
            .then(
                if (onClick != null) Modifier.clip(CircleShape).toggleable(checked, role = Role.Checkbox, onValueChange = { onClick() })
                else Modifier,
            )
            .semantics { if (onClick != null) contentDescription = label }
            .testTag("task-circle"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = 2.dp.toPx()
            val radius = size.minDimension / 2 - stroke / 2
            val ring = if (attention) c.warnSolid else c.ink3
            drawCircle(ring.copy(alpha = if (running) 0.3f else 0.85f), radius, style = Stroke(stroke))
            if (running && !checked) {
                val inset = stroke / 2
                drawArc(
                    c.accent, startAngle = -90f + (angle?.value ?: 0f), sweepAngle = progress?.let { 360f * it.coerceIn(0.02f, 1f) } ?: 100f,
                    useCenter = false, topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            if (fill > 0.01f) {
                drawCircle(c.accent, size.minDimension / 2 * fill.coerceAtMost(1.12f))
                val w = size.width; val h = size.height
                val check = Path().apply { moveTo(w * 0.28f, h * 0.52f); lineTo(w * 0.44f, h * 0.67f); lineTo(w * 0.73f, h * 0.36f) }
                val shown = Path()
                val measure = PathMeasure().apply { setPath(check, false) }
                measure.getSegment(0f, measure.length * fill.coerceIn(0f, 1f), shown, true)
                drawPath(shown, c.onAccent, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** The Undo bar: a quiet floating surface in the app's own shapes, message on the left, Undo on the right. */
@Composable
private fun UndoBar(data: SnackbarData) {
    val c = MikaTheme.colors
    Row(
        Modifier.fillMaxWidth().floatShadow(c, MikaShapes.Card).clip(MikaShapes.Card).background(c.surface)
            .border(0.75.dp, c.glassEdge, MikaShapes.Card).padding(start = 18.dp, end = 6.dp).heightIn(min = 56.dp)
            .testTag("tasks-undo-bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(data.visuals.message, Modifier.weight(1f).padding(vertical = 10.dp), style = MikaType.Body, color = c.ink)
        data.visuals.actionLabel?.let { label ->
            Box(
                Modifier.heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill)
                    .clickable(role = Role.Button, onClick = data::performAction).padding(horizontal = 14.dp).testTag("tasks-undo"),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MikaType.BodyStrong, color = c.accent) }
        }
    }
}

@Composable
private fun ScheduleRow(plan: TriggerDefinition, stopped: Boolean = false, onClick: () -> Unit) {
    val c = MikaTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale) }
    val time = remember(plan.nextAtMs, format) { format.format(Instant.ofEpochMilli(plan.nextAtMs).atZone(ZoneId.systemDefault())) }
    Row(Modifier.fillMaxWidth().clip(MikaShapes.Card).clickable(role = Role.Button, onClick = onClick).padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 3.dp).size(19.dp).border(1.dp, c.hair, RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f)) {
            Text(plan.reminderText ?: plan.goal, style = MikaType.BodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (stopped) stringResource(R.string.chat_paused) else time, Modifier.padding(top = 4.dp), style = MikaType.Caption,
                color = if (stopped) c.ink3 else c.ink2)
        }
        Icon(MikaIcons.Chevron, null, Modifier.size(18.dp), tint = c.ink3)
    }
    HorizontalDivider(color = c.hair)
}

/**
 * The large page title kept to one line: it shrinks just enough to fit narrow screens and large font
 * sizes instead of wrapping, never below 22sp.
 */
@Composable
internal fun OneLineTitle(text: AnnotatedString, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val full = MikaType.LargeTitle
        val style = remember(text, maxWidth, measurer) {
            val width = measurer.measure(text, full, maxLines = 1, softWrap = false).size.width
            val room = constraints.maxWidth
            if (width <= room || width == 0) full else {
                val size = (full.fontSize.value * room / width * 0.98f).coerceAtLeast(22f)
                full.copy(fontSize = size.sp, lineHeight = (size * 1.25f).sp)
            }
        }
        // The estimate can miss by a few pixels once the real font loads; shrink again until the whole title fits.
        var fit by remember(text, maxWidth) { mutableStateOf(1f) }
        Text(
            text, style = if (fit == 1f) style else style.copy(fontSize = (style.fontSize.value * fit).sp, lineHeight = (style.fontSize.value * fit * 1.25f).sp), color = color,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            onTextLayout = { if ((it.didOverflowWidth || it.hasVisualOverflow) && fit > 0.6f) fit *= 0.94f },
        )
    }
}

/** Orbit has started real work: it used a tool, or the run stopped to ask the user something. */
private fun startedWork(state: TaskState): Boolean = when (state) {
    is TaskState.Running -> state.tool != null || state.progress != null
    TaskState.Idle -> false
    is TaskState.Terminal -> false
    else -> true
}

private fun TaskState.isQueued(): Boolean = this is TaskState.Held && reason.queued
