package com.wangzan101.mika.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.wangzan101.mika.R
import com.wangzan101.mika.chat.ConversationStore
import com.wangzan101.mika.chat.FileLibrary
import com.wangzan101.mika.chat.FileSource
import com.wangzan101.mika.chat.LibraryFile
import com.wangzan101.mika.ui.components.BarIconButton
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.identity.PresenceState
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One file the agent produced in a conversation (the newest copy when the same file was shared again). */
@Immutable
internal data class MadeArtifact(
    val key: String,
    val name: String,
    val size: String,
    val atMs: Long?,
    val handle: LibraryFileHandle,
    val source: FileSource,
)

/** Only what the agent created: shared outputs, never the user's own uploads. Newest first. */
internal fun madeArtifacts(files: List<LibraryFile>): List<MadeArtifact> =
    files.filterIsInstance<LibraryFile.Output>().mapNotNull { file ->
        file.sources.maxByOrNull { it.source.atMs ?: Long.MIN_VALUE }?.let { latest ->
            MadeArtifact(
                LibrarySelection.outputKey(latest.file, latest.source),
                latest.file.name, ChatViewModel.sizeLabel(latest.file.size), file.latestAtMs,
                LibraryFileHandle.Exported(latest.file), latest.source,
            )
        }
    }.sortedByDescending { it.atMs ?: Long.MIN_VALUE }

/**
 * The Make tab: the artifacts Orbit has made for the user, and nothing else.
 * Browser, files, memory and skills live behind the Orbit hub (and the drawer).
 */
@Composable
internal fun MakeScreen(
    onOpenDrawer: () -> Unit,
    onOpenOrbitHub: () -> Unit = {},
    presence: PresenceState = PresenceState.Idle,
    bottomInset: Dp = 0.dp,
    /** Jumps to the message that produced the file; returns whether the conversation opened. */
    onOpenSource: (String, ChatTarget) -> Boolean = { _, _ -> false },
    conversationStore: ConversationStore? = null,
    load: (suspend () -> List<MadeArtifact>)? = null,
) {
    val c = MikaTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(conversationStore, load) { if (load == null) conversationStore ?: ConversationStore.shared(context) else null }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(store) { store?.changes?.collect { revision++ } }
    // null while the first read runs; later revisions keep the old list on screen (no "loading" flash)
    val artifacts by produceState<List<MadeArtifact>?>(null, revision, store, load) {
        value = try {
            load?.invoke() ?: withContext(Dispatchers.IO) { madeArtifacts(FileLibrary(store!!).snapshot().files) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            value ?: emptyList()
        }
    }
    Column(Modifier.fillMaxSize().background(c.bg).testTag("make-screen")) {
        TopBar("", MikaIcons.Menu, onOpenDrawer, stringResource(R.string.chat_menu),
            titleIcon = { OrbitHubButton(presence, onOpenOrbitHub) }, centerTitle = true,
            barHeight = orbitHeaderBarHeight())
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 26.dp, bottom = bottomInset + 26.dp),
        ) {
            item("title") {
                Column(Modifier.padding(bottom = 22.dp)) {
                    Text(stringResource(R.string.nav_make), style = MikaType.LargeTitle, color = c.ink,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.orbit_make_subtitle), Modifier.padding(top = 8.dp),
                        style = MikaType.Body, color = c.ink2)
                }
            }
            val list = artifacts
            when {
                list == null -> item("loading") {
                    Text(stringResource(R.string.file_library_loading), Modifier.padding(vertical = 12.dp),
                        style = MikaType.Body, color = c.ink3)
                }
                list.isEmpty() -> item("empty") { MakeEmpty() }
                else -> item("artifacts") {
                    MikaCard {
                        list.forEachIndexed { i, artifact ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = c.hair)
                            ArtifactRow(
                                artifact,
                                onOpen = {
                                    scope.launch {
                                        if (!LibraryFileActions.preview(context, artifact.handle)) {
                                            Toast.makeText(context, R.string.file_library_error, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onShowInChat = {
                                    val target = fileLibraryTarget(artifact.source, input = false)
                                    if (!onOpenSource(artifact.source.conversationId, target)) {
                                        Toast.makeText(context, R.string.file_library_open_failed, Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MakeEmpty() {
    val c = MikaTheme.colors
    Column(Modifier.fillMaxWidth().testTag("make-empty").padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).background(c.answer, MikaShapes.Tile), contentAlignment = Alignment.Center) {
            Icon(MikaIcons.Spark, null, Modifier.size(26.dp), tint = c.ink2)
        }
        Text(stringResource(R.string.orbit_make_empty), Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp),
            style = MikaType.Body, color = c.ink3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ArtifactRow(artifact: MadeArtifact, onOpen: () -> Unit, onShowInChat: () -> Unit) {
    val c = MikaTheme.colors
    val preview = stringResource(R.string.file_library_preview)
    val meta = listOfNotNull(
        artifact.size,
        artifact.atMs?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() },
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().testTag("make-artifact").clip(MikaShapes.Card)
            .clickable(role = Role.Button, onClickLabel = preview, onClick = onOpen)
            .heightIn(min = 68.dp).padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(c.answer, MikaShapes.Tile), contentAlignment = Alignment.Center) {
            Icon(MikaIcons.Doc, null, Modifier.size(21.dp), tint = c.ink)
        }
        Column(Modifier.weight(1f)) {
            Text(artifact.name, style = MikaType.BodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MikaType.Caption, color = c.ink3, maxLines = 1)
        }
        BarIconButton(MikaIcons.Message, stringResource(R.string.file_library_open_in_chat), onShowInChat)
    }
}
