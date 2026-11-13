package com.wangzan101.mika.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.personal.PersonalRuntime
import com.wangzan101.mika.chat.ConversationStore
import com.wangzan101.mika.chat.FileLibrary
import com.wangzan101.mika.chat.FileLibrarySnapshot
import com.wangzan101.mika.chat.LibraryFile
import com.wangzan101.mika.browser.UserBrowser
import com.wangzan101.mika.memory.MemoryRuntime
import com.wangzan101.mika.core.memory.MemoryStatus
import com.wangzan101.mika.core.memory.MemoryStore
import com.wangzan101.mika.core.skills.PersonalSkill
import com.wangzan101.mika.settings.FeatureFlags
import com.wangzan101.mika.settings.OrbitIdentityMode
import com.wangzan101.mika.core.run.RunPhase
import com.wangzan101.mika.core.run.RunStore
import com.wangzan101.mika.core.run.StoredRun
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.task.TaskCenterState
import com.wangzan101.mika.task.TriggerRuntime
import com.wangzan101.mika.core.trigger.TriggerSnapshot
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.identity.MikaPresenceAvatar
import com.wangzan101.mika.ui.identity.PresenceState
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaMotion
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** The header and expanded hub use the same state-driven presence. */
@Composable
internal fun OrbitHubButton(state: PresenceState, onClick: () -> Unit, size: Dp = 52.dp) {
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val label = stringResource(R.string.orbit_hub_open)
    Box(Modifier.size(size).clip(MikaShapes.Pill)
        .semantics { contentDescription = label }
        .clickable(role = Role.Button) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            keyboard?.hide()
            onClick()
        }.testTag("orbit-hub-open"), contentAlignment = Alignment.Center) {
        MikaPresenceAvatar(Modifier.fillMaxSize(), state = state, framed = true)
    }
}

private data class HubSection(val label: Int, val icon: ImageVector)
private data class HubMemoryData(val store: MemoryStore, val skills: List<PersonalSkill>)
private class HubSectionCache {
    var memory by mutableStateOf<Result<HubMemoryData>?>(null)
    var files by mutableStateOf<Result<FileLibrarySnapshot>?>(null)
    var filesRevision by mutableIntStateOf(0)
}
private val hubSections = listOf(
    HubSection(R.string.orbit_hub_today, MikaIcons.Clock),
    HubSection(R.string.drawer_browser, MikaIcons.Globe),
    HubSection(R.string.memory_and_skills, MikaIcons.Spark),
    HubSection(R.string.file_library_title, MikaIcons.Doc),
    HubSection(R.string.orbit_hub_personalization, MikaIcons.Gear),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OrbitHub(
    state: PresenceState,
    expansion: Float,
    ready: Boolean,
    personal: PersonalRuntime,
    taskState: TaskCenterState,
    runStore: RunStore,
    memory: MemoryRuntime,
    onClose: () -> Unit,
    onTasks: () -> Unit,
    onPlans: () -> Unit,
    onOpenTask: (String) -> Unit,
    onBrowser: (String?) -> Unit,
    onMemory: () -> Unit,
    onFiles: () -> Unit,
    onPersonalization: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val c = MikaTheme.colors
    val context = LocalContext.current
    val cache = remember { HubSectionCache() }
    LaunchedEffect(context, cache) {
        ConversationStore.shared(context).changes.collect {
            cache.files = null
            cache.filesRevision++
        }
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    val collapseOffset = with(LocalDensity.current) { 96.dp.roundToPx() }
    val collapsed by remember(list, collapseOffset) {
        derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset >= collapseOffset }
    }
    val presenceLimit = with(LocalDensity.current) { 200.dp.roundToPx() }
    val presenceVisible by remember(list, presenceLimit) {
        derivedStateOf { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < presenceLimit }
    }
    Box(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().testTag("orbit-hub")) {
        LazyColumn(Modifier.fillMaxSize().padding(top = 48.dp).testTag("orbit-hub-scroll"), state = list) {
            item(key = "presence") {
                Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Space for the presence, drawn above the list to preserve its entrance path.
                    Box(Modifier.size(128.dp))
                    Text(stringResource(R.string.app_name), Modifier.padding(top = 14.dp), style = MikaType.Title, color = c.ink)
                    Text(stringResource(presenceLabel(state, ready)), Modifier.padding(top = 5.dp), style = MikaType.Caption, color = c.ink2)
                }
            }
            stickyHeader(key = "sections") {
                Box(Modifier.fillMaxWidth().background(c.bg)) { OrbitHubTabs(selected) { selected = it } }
            }
            item(key = "content") {
                val contentModifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                Column(if (MikaTheme.reduceMotion) contentModifier else contentModifier.animateContentSize()) {
                    val section = hubSections[selected]
                    Text(stringResource(section.label), style = MikaType.Title, color = c.ink,
                        modifier = Modifier.padding(bottom = 14.dp))
                    when (selected) {
                        0 -> HubToday(personal, taskState, runStore, onTasks, onPlans, onOpenTask)
                        1 -> HubBrowser(onBrowser)
                        2 -> HubMemory(memory, cache, onMemory)
                        3 -> HubFiles(cache, onFiles)
                        else -> HubPersonalization(onPersonalization)
                    }
                }
            }
        }
        if (presenceVisible) {
            // Draw outside the lazy viewport so the avatar can move from the compact header.
            Box(Modifier.align(Alignment.TopCenter).padding(top = 66.dp).size(128.dp)
                .graphicsLayer { translationY = -list.firstVisibleItemScrollOffset.toFloat() },
                contentAlignment = Alignment.Center) {
                MikaPresenceAvatar(
                    Modifier.size(52.dp + 76.dp * expansion).graphicsLayer {
                        translationY = -102.dp.toPx() * (1f - expansion)
                    }, state = state, framed = true,
                )
            }
        }
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter)
            .background(if (collapsed) c.bg else c.bg.copy(alpha = 0f)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            HubIconButton(MikaIcons.Close, stringResource(R.string.personal_close), onClose)
            Crossfade(collapsed, Modifier.weight(1f), animationSpec = MikaMotion.switch(MikaTheme.reduceMotion), label = "hub-header") { compact ->
                if (compact) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        MikaPresenceAvatar(Modifier.size(32.dp), state = state, framed = true)
                        Text(stringResource(R.string.app_name), Modifier.padding(start = 8.dp), style = MikaType.BodyStrong, color = c.ink)
                    }
                } else {
                    Text(stringResource(R.string.orbit_hub_title).uppercase(), style = MikaType.Label, color = c.ink3, textAlign = TextAlign.Center)
                }
            }
            HubIconButton(MikaIcons.Gear, stringResource(R.string.orbit_hub_personalization), onPersonalization)
        }
    }
}

@Composable
internal fun OrbitHubTabs(selected: Int, onSelect: (Int) -> Unit) {
    val c = MikaTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)
        .clip(MikaShapes.Pill).background(c.surface).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly) {
        hubSections.forEachIndexed { index, section ->
            val label = stringResource(section.label)
            val background by animateColorAsState(
                if (selected == index) c.answer else c.surface,
                animationSpec = MikaMotion.switch(MikaTheme.reduceMotion), label = "hub-tab-background",
            )
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(MikaShapes.Pill)
                .background(background)
                .selectable(selected == index, role = Role.Tab) { onSelect(index) }
                .semantics { contentDescription = label }
                .testTag("orbit-hub-section-$index"), contentAlignment = Alignment.Center) {
                Icon(section.icon, contentDescription = null, Modifier.size(21.dp), tint = if (selected == index) c.ink else c.ink3)
            }
        }
    }
}

@Composable
private fun HubIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(MikaShapes.Pill).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, Modifier.size(22.dp), tint = MikaTheme.colors.ink)
    }
}

@Composable
private fun HubToday(personal: PersonalRuntime, taskState: TaskCenterState, runStore: RunStore, onTasks: () -> Unit, onPlans: () -> Unit, onOpenTask: (String) -> Unit) {
    val c = MikaTheme.colors
    val context = LocalContext.current
    val snapshot by personal.state.collectAsState()
    val runs by taskState.runs.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
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
    val history by produceState<Result<List<StoredRun>>?>(null, runs.map { it.key }) {
        value = try {
            Result.success(withContext(Dispatchers.IO) {
                val conversations = ConversationStore.shared(context).list(includeArchived = true).mapTo(mutableSetOf()) { it.id }
                runStore.all().filter { it.threadId in conversations && (it.phase == RunPhase.Ended || it.phase == RunPhase.Interrupted) }.asReversed()
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    val personalIds = snapshot.tasks.mapTo(mutableSetOf()) { it.conversationId }
    val liveIds = runs.mapNotNullTo(mutableSetOf()) { it.runId.value }
    val recent = history?.getOrNull().orEmpty().filter {
        it.threadId !in personalIds && (it.recordId == null || it.recordId !in liveIds)
    }.take(3)
    MikaCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(stringResource(R.string.orbit_hub_activity), style = MikaType.BodyStrong, color = c.ink)
            if (tasks.isEmpty() && otherRuns.isEmpty() && plans.isEmpty() && recent.isEmpty() && schedules?.isSuccess == true && history?.isSuccess == true) {
                Text(stringResource(R.string.orbit_hub_empty), Modifier.padding(top = 12.dp), style = MikaType.Caption, color = c.ink2)
            }
            tasks.forEach { task -> key(task.id) {
                val run = runs.firstOrNull { it.conversationId == task.conversationId }
                val live = run?.state?.collectAsState()?.value
                val status = terminalStatus(live) ?: if (live == null) personalStatus(task) else null
                HubTask(task.title, taskSection(task, live), { onOpenTask(task.conversationId) }, statusOverride = status)
            } }
            otherRuns.forEach { run -> key(run.key) {
                val live by run.state.collectAsState()
                val status = terminalStatus(live)
                taskSection(live)?.let { section -> HubTask(run.goal, section, { onOpenTask(run.conversationId) }, statusOverride = status) }
            } }
            recent.forEach { run ->
                val status = when {
                    run.phase == RunPhase.Interrupted || run.runSucceeded == false -> stringResource(R.string.personal_status_incomplete)
                    run.runSucceeded == true -> stringResource(R.string.personal_status_completed)
                    else -> null
                }
                HubTask(run.userText ?: run.goal, TaskSection.Resolved, { onOpenTask(run.threadId) }, statusOverride = status)
            }
            plans.forEach { plan ->
                val whenText = remember(plan.nextAtMs, locale) {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(plan.nextAtMs))
                }
                HubTask(plan.reminderText ?: plan.goal, TaskSection.Upcoming, onPlans, whenText)
            }
            if (schedules == null || history == null) Text(stringResource(R.string.loading), style = MikaType.Caption, color = c.ink2)
            if (schedules?.isFailure == true || history?.isFailure == true) Text(stringResource(R.string.op_failed), style = MikaType.Caption, color = c.bad)
            TextButton(onClick = onTasks) { Text(stringResource(R.string.orbit_hub_tasks)) }
        }
    }
}

@Composable
private fun HubTask(title: String, section: TaskSection, onClick: () -> Unit, detail: String? = null, statusOverride: String? = null) {
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
        val statusText = statusOverride ?: stringResource(status)
        Text(if (detail == null) statusText else "$statusText · $detail", style = MikaType.Caption, color = c.ink2)
    }
}

@Composable
private fun terminalStatus(state: TaskState?): String? = when (state) {
    TaskState.Cancelled -> stringResource(R.string.personal_status_cancelled)
    is TaskState.Failed -> stringResource(R.string.personal_status_incomplete)
    is TaskState.Succeeded -> stringResource(R.string.personal_status_completed)
    else -> null
}

@Composable
private fun HubBrowser(onOpen: (String?) -> Unit) {
    val c = MikaTheme.colors
    val context = LocalContext.current
    val browser = remember(context) { UserBrowser.of(context) }
    val page by browser.page.collectAsState()
    val recent by browser.recent.entries.collectAsState()
    MikaCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(stringResource(R.string.browser_recent_source), style = MikaType.BodyStrong, color = c.ink)
            page?.let { current -> HubEntry(current.title.ifBlank { current.url }) { onOpen(null) } }
            val pages = recent.filter { it.url != page?.url }.take(3)
            if (page == null && pages.isEmpty()) HubHint(stringResource(R.string.browser_no_recent))
            pages.forEach { item -> HubEntry(item.title.ifBlank { item.url }) { onOpen(item.url) } }
            TextButton(onClick = { onOpen(null) }) { Text(stringResource(R.string.orbit_hub_open_section)) }
        }
    }
}

@Composable
private fun HubMemory(memory: MemoryRuntime, cache: HubSectionCache, onOpen: () -> Unit) {
    val c = MikaTheme.colors
    LaunchedEffect(memory, cache) {
        if (cache.memory == null) {
            cache.memory = try {
                Result.success(withContext(Dispatchers.IO) { HubMemoryData(memory.store(), memory.skills().list()) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    val loaded = cache.memory
    val data = loaded?.getOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MikaCard {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(stringResource(R.string.memory_memory), style = MikaType.BodyStrong, color = c.ink)
                when {
                    loaded == null -> HubHint(stringResource(R.string.loading))
                    data == null || !data.store.available -> HubHint(stringResource(R.string.memory_unavailable))
                    else -> {
                        val items by data.store.items.collectAsState()
                        val active = items.filter { it.status == MemoryStatus.active }.sortedByDescending { it.updatedAt }.take(3)
                        if (active.isEmpty()) HubHint(stringResource(R.string.memory_empty))
                        active.forEach { item -> HubEntry(item.content, onOpen) }
                        if (items.any { it.status == MemoryStatus.pending }) HubEntry(stringResource(R.string.memory_pending), onOpen)
                    }
                }
                TextButton(onClick = onOpen) { Text(stringResource(R.string.orbit_hub_open_section)) }
            }
        }
        MikaCard {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(stringResource(R.string.memory_skills), style = MikaType.BodyStrong, color = c.ink)
                when {
                    loaded == null -> HubHint(stringResource(R.string.loading))
                    data == null -> HubHint(stringResource(R.string.memory_unavailable))
                    data.skills.isEmpty() -> HubHint(stringResource(R.string.skills_empty))
                    else -> data.skills.take(3).forEach { skill -> HubEntry(skill.name, onOpen) }
                }
                TextButton(onClick = onOpen) { Text(stringResource(R.string.orbit_hub_open_section)) }
            }
        }
    }

}

@Composable
private fun HubFiles(cache: HubSectionCache, onOpen: () -> Unit) {
    val c = MikaTheme.colors
    val context = LocalContext.current
    LaunchedEffect(context, cache.filesRevision) {
        if (cache.files == null) {
            cache.files = try {
                Result.success(withContext(Dispatchers.IO) { FileLibrary(ConversationStore.shared(context)).snapshot() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    val loaded = cache.files
    MikaCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            val snapshot = loaded?.getOrNull()
            when {
                loaded == null -> HubHint(stringResource(R.string.file_library_loading))
                snapshot == null -> HubHint(stringResource(R.string.file_library_load_failed))
                else -> {
                    val files = snapshot.files.sortedByDescending {
                        when (it) {
                            is LibraryFile.Input -> it.latestAtMs
                            is LibraryFile.Output -> it.latestAtMs
                        } ?: 0L
                    }.take(3)
                    if (files.isEmpty() && !snapshot.hasReadFailures) HubHint(stringResource(R.string.file_library_empty))
                    files.forEach { file ->
                        val name = when (file) {
                            is LibraryFile.Input -> file.name
                            is LibraryFile.Output -> file.sources.maxByOrNull { it.source.atMs ?: 0L }?.file?.name.orEmpty()
                        }
                        HubEntry(name, onOpen)
                    }
                    if (snapshot.hasReadFailures) HubHint(stringResource(R.string.file_library_partial))
                }
            }
            TextButton(onClick = onOpen) { Text(stringResource(R.string.orbit_hub_open_section)) }
        }
    }
}

@Composable
private fun HubPersonalization(onOpen: () -> Unit) {
    val c = MikaTheme.colors
    val context = LocalContext.current
    val flags = remember(context) { FeatureFlags.get(context) }
    val mode by remember(flags) { flags.themeModeChanges() }.collectAsState(flags.themeMode())
    val identity = MikaTheme.identity
    MikaCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(stringResource(R.string.appearance_group), style = MikaType.BodyStrong, color = c.ink)
            HubEntry(appearanceLabel(mode), onOpen)
            val identityLabel = stringResource(if (identity == OrbitIdentityMode.Original) R.string.orbit_identity_original else R.string.orbit_identity_monochrome)
            HubEntry("${stringResource(R.string.orbit_identity)} · $identityLabel", onOpen)
            TextButton(onClick = onOpen) { Text(stringResource(R.string.orbit_hub_open_section)) }
        }
    }
}

@Composable
private fun HubEntry(title: String, onOpen: () -> Unit) {
    Text(title, Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen)
        .heightIn(min = 48.dp).padding(vertical = 12.dp), style = MikaType.Body, color = MikaTheme.colors.ink,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun HubHint(text: String) {
    Text(text, Modifier.padding(top = 12.dp), style = MikaType.Caption, color = MikaTheme.colors.ink2)
}

private fun presenceLabel(state: PresenceState, ready: Boolean): Int = when (state) {
    PresenceState.Idle -> if (ready) R.string.orbit_hub_ready else R.string.banner_no_model
    PresenceState.Listening -> R.string.orbit_hub_listening
    PresenceState.Thinking -> R.string.orbit_hub_thinking
    PresenceState.Working -> R.string.orbit_hub_working
    PresenceState.NeedsUser -> R.string.orbit_hub_needs_you
    PresenceState.Success -> R.string.orbit_hub_success
    PresenceState.Error -> R.string.orbit_hub_error
}
