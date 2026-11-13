package com.wangzan101.mika.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.R
import com.wangzan101.mika.personal.PersonalRuntime
import com.wangzan101.mika.task.TaskCenterState
import com.wangzan101.mika.task.TriggerRuntime
import com.wangzan101.mika.core.trigger.TriggerSnapshot
import com.wangzan101.mika.ui.components.BarIconButton
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.identity.MikaPresenceAvatar
import com.wangzan101.mika.ui.identity.PresenceState
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The header and expanded hub use the same state-driven presence. */
@Composable
internal fun OrbitHubButton(state: PresenceState, onClick: () -> Unit, size: Dp = 52.dp) {
    val haptic = LocalHapticFeedback.current
    val label = stringResource(R.string.orbit_hub_open)
    Box(Modifier.size(size).clip(MikaShapes.Pill)
        .semantics { contentDescription = label }
        .clickable(role = Role.Button) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }.testTag("orbit-hub-open"), contentAlignment = Alignment.Center) {
        MikaPresenceAvatar(Modifier.fillMaxSize(), state = state, framed = true)
    }
}

private data class HubSection(val label: Int, val icon: ImageVector)
private val hubSections = listOf(
    HubSection(R.string.orbit_hub_today, MikaIcons.Clock),
    HubSection(R.string.drawer_browser, MikaIcons.Globe),
    HubSection(R.string.memory_and_skills, MikaIcons.Spark),
    HubSection(R.string.file_library_title, MikaIcons.Doc),
    HubSection(R.string.orbit_hub_personalization, MikaIcons.Gear),
)

@Composable
internal fun OrbitHub(
    state: PresenceState,
    personal: PersonalRuntime,
    taskState: TaskCenterState,
    onClose: () -> Unit,
    onTasks: () -> Unit,
    onPlans: () -> Unit,
    onOpenTask: (String) -> Unit,
    onBrowser: () -> Unit,
    onMemory: () -> Unit,
    onFiles: () -> Unit,
    onPersonalization: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val c = MikaTheme.colors
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).testTag("orbit-hub")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BarIconButton(MikaIcons.Close, stringResource(R.string.personal_close), onClose)
            Text(stringResource(R.string.orbit_hub_title).uppercase(), Modifier.weight(1f),
                style = MikaType.Label, color = c.ink3, textAlign = TextAlign.Center)
            BarIconButton(MikaIcons.Gear, stringResource(R.string.orbit_hub_personalization), onPersonalization)
        }
        Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MikaPresenceAvatar(Modifier.size(128.dp), state = state, framed = true)
            Text(stringResource(R.string.app_name), Modifier.padding(top = 14.dp), style = MikaType.Title, color = c.ink)
            Text(stringResource(presenceLabel(state)), Modifier.padding(top = 5.dp), style = MikaType.Caption, color = c.ink2)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)
            .clip(MikaShapes.Pill).background(c.surface).padding(4.dp).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            hubSections.forEachIndexed { index, section ->
                val label = stringResource(section.label)
                Column(Modifier.weight(1f).clip(MikaShapes.Pill)
                    .background(if (selected == index) c.answer else c.surface)
                    .selectable(selected == index, role = Role.Tab) { selected = index }
                    .heightIn(min = 64.dp).padding(horizontal = 2.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(section.icon, contentDescription = null, Modifier.size(21.dp), tint = if (selected == index) c.ink else c.ink3)
                    Text(label, Modifier.padding(top = 3.dp), color = if (selected == index) c.ink else c.ink3,
                        fontSize = 10.sp, lineHeight = 12.sp, fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
        val contentModifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        Column(if (MikaTheme.reduceMotion) contentModifier else contentModifier.animateContentSize()) {
            val section = hubSections[selected]
            Text(stringResource(section.label), style = MikaType.Title, color = c.ink,
                modifier = Modifier.padding(bottom = 14.dp))
            when (selected) {
                0 -> HubToday(personal, taskState, onTasks, onPlans, onOpenTask)
                1 -> HubDestination(R.string.orbit_hub_browser_description, onBrowser)
                2 -> HubDestination(R.string.orbit_hub_memory_description, onMemory)
                3 -> HubDestination(R.string.orbit_hub_files_description, onFiles)
                else -> HubDestination(R.string.orbit_hub_personalization_description, onPersonalization)
            }
        }
    }
}

@Composable
private fun HubToday(personal: PersonalRuntime, taskState: TaskCenterState, onTasks: () -> Unit, onPlans: () -> Unit, onOpenTask: (String) -> Unit) {
    val c = MikaTheme.colors
    val context = LocalContext.current
    val snapshot by personal.state.collectAsState()
    val runs by taskState.runs.collectAsState()
    val schedules by produceState<Result<TriggerSnapshot>?>(null) {
        value = try {
            Result.success(withContext(Dispatchers.IO) { TriggerRuntime.snapshot(context) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    val tasks = remember(snapshot.tasks) { snapshot.tasks.sortedByDescending { it.at }.take(4) }
    val otherRuns = runs.filter { run -> snapshot.tasks.none { it.conversationId == run.conversationId } }.take(4)
    val plans = schedules?.getOrNull()?.triggers.orEmpty().filter { it.enabled }.sortedBy { it.nextAtMs }.take(3)
    MikaCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(stringResource(R.string.orbit_hub_activity), style = MikaType.BodyStrong, color = c.ink)
            if (tasks.isEmpty() && otherRuns.isEmpty() && plans.isEmpty() && schedules?.isSuccess == true) {
                Text(stringResource(R.string.orbit_hub_empty), Modifier.padding(top = 12.dp), style = MikaType.Caption, color = c.ink2)
            }
            tasks.forEach { task -> key(task.id) {
                val run = runs.firstOrNull { it.conversationId == task.conversationId }
                val live = run?.state?.collectAsState()?.value
                HubTask(task.title, taskSection(task, live), { onOpenTask(task.conversationId) })
            } }
            otherRuns.forEach { run -> key(run.key) {
                val live by run.state.collectAsState()
                taskSection(live)?.let { section -> HubTask(run.goal, section, { onOpenTask(run.conversationId) }) }
            } }
            plans.forEach { plan -> HubTask(plan.reminderText ?: plan.goal, TaskSection.Upcoming, onPlans) }
            if (schedules == null) Text(stringResource(R.string.loading), style = MikaType.Caption, color = c.ink2)
            if (schedules?.isFailure == true) Text(stringResource(R.string.op_failed), style = MikaType.Caption, color = c.bad)
            TextButton(onClick = onTasks) { Text(stringResource(R.string.orbit_hub_tasks)) }
        }
    }
}

@Composable
private fun HubTask(title: String, section: TaskSection, onClick: () -> Unit) {
    val c = MikaTheme.colors
    val status = when (section) {
        TaskSection.Review -> R.string.tasks_review
        TaskSection.NeedsYou -> R.string.personal_status_needs_you
        TaskSection.Working -> R.string.chat_working
        TaskSection.Waiting -> R.string.tasks_waiting
        TaskSection.Upcoming -> R.string.personal_status_queued
        TaskSection.Resolved -> R.string.tasks_resolved
    }
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, style = MikaType.Body, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(stringResource(status), style = MikaType.Caption, color = c.ink2)
    }
}

@Composable
private fun HubDestination(description: Int, onOpen: () -> Unit) {
    val c = MikaTheme.colors
    MikaCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(stringResource(description), style = MikaType.Body, color = c.ink2)
            TextButton(onClick = onOpen) { Text(stringResource(R.string.orbit_hub_open_section)) }
        }
    }
}

private fun presenceLabel(state: PresenceState): Int = when (state) {
    PresenceState.Idle -> R.string.orbit_hub_ready
    PresenceState.Listening -> R.string.orbit_hub_listening
    PresenceState.Thinking -> R.string.orbit_hub_thinking
    PresenceState.Working -> R.string.orbit_hub_working
    PresenceState.NeedsUser -> R.string.orbit_hub_needs_you
    PresenceState.Success -> R.string.orbit_hub_success
    PresenceState.Error -> R.string.orbit_hub_error
}
