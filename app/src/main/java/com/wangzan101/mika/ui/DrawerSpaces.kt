package com.wangzan101.mika.ui

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wangzan101.mika.R
import com.wangzan101.mika.cap.sandbox.SandboxDirs
import com.wangzan101.mika.spaces.Space
import com.wangzan101.mika.spaces.SpacePanel
import com.wangzan101.mika.ui.components.BarIconButton
import com.wangzan101.mika.ui.components.DirectionalIcon
import com.wangzan101.mika.ui.components.HeaderPill
import com.wangzan101.mika.ui.components.KeyboardDismissArea
import com.wangzan101.mika.ui.components.keepsKeyboard
import com.wangzan101.mika.ui.components.MikaDialog
import com.wangzan101.mika.ui.components.cardSurface
import com.wangzan101.mika.ui.components.floatShadow
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay

// ---------- Sidebar pieces: tiles, rows, section headers ----------

internal val DrawerCardShape = RoundedCornerShape(20.dp)
private val TileShape = RoundedCornerShape(9.dp)
private val RowTitle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
private val RowSub = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp)
private val HeaderText = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)

/** Channel and space colors: a fixed set, picked by name so each keeps its color. */
private val TileColors = listOf(
    Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF9F0A), Color(0xFF30D158),
    Color(0xFFFF375F), Color(0xFF5E5CE6), Color(0xFF64D2FF), Color(0xFFFFD60A),
)

internal fun tileColor(key: String, shift: Int = 0): Color =
    TileColors[(Math.floorMod(key.hashCode(), TileColors.size) + shift) % TileColors.size]

/** A channel's icon: a colored rounded square with a hash. */
@Composable
internal fun ChannelTile(name: String, size: Dp = 30.dp) {
    Box(Modifier.size(size).background(tileColor(name), TileShape), contentAlignment = Alignment.Center) {
        Text("#", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = Color.White)
    }
}

/** A space's icon: its emoji on a soft two-color gradient. */
@Composable
internal fun SpaceTile(space: Space, size: Dp = 30.dp) {
    val brush = remember(space.id) {
        Brush.linearGradient(listOf(tileColor(space.id), tileColor(space.id, shift = 1)))
    }
    Box(Modifier.size(size).background(brush, TileShape), contentAlignment = Alignment.Center) {
        Text(space.emoji, style = TextStyle(fontSize = (size.value * 0.52f).sp))
    }
}

/** A plain icon on a rounded square, for Everything and Main chat. */
@Composable
internal fun IconTile(icon: ImageVector, tint: Color, background: Color) {
    Box(Modifier.size(32.dp).background(background, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
    }
}

/** One link row in the sidebar's top card: tile, title, one line under it. */
@Composable
internal fun DrawerLink(
    tile: @Composable () -> Unit,
    title: String,
    subtitle: String,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = MikaTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(DrawerCardShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp).testTagged(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        tile()
        Column(Modifier.weight(1f)) {
            Text(title, style = RowTitle, color = if (enabled) c.ink else c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = RowSub, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A hairline inside the top card, starting under the titles. */
@Composable
internal fun CardHairline() {
    val c = MikaTheme.colors
    Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(0.5.dp).background(c.hair))
}

/** A section title with a small round plus on the right (Channels, Spaces). */
@Composable
internal fun SectionHeader(text: String, addLabel: String? = null, addTag: String = "", enabled: Boolean = true, onAdd: () -> Unit = {}) {
    val c = MikaTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f).semantics { heading() }, style = HeaderText, color = c.ink2)
        if (addLabel != null) {
            Box(
                Modifier.size(MikaSize.TouchTarget).clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = addLabel, onClick = onAdd).testTagged(addTag),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(28.dp).background(c.fill, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(MikaIcons.Plus, addLabel, Modifier.size(14.dp), tint = if (enabled) c.ink2 else c.ink3)
                }
            }
        }
    }
}

/** "+ New channel" and the dashed "Create a space": a quiet row at the end of a section. */
@Composable
internal fun AddRow(text: String, tag: String, dashed: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = MikaTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth().heightIn(min = 48.dp).clip(shape)
            .then(
                if (dashed) Modifier.drawBehind {
                    val r = 16.dp.toPx()
                    drawRoundRect(
                        c.edge, cornerRadius = CornerRadius(r, r),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                    )
                } else Modifier,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp).testTagged(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Icon(MikaIcons.Plus, null, Modifier.size(16.dp), tint = if (enabled) c.ink2 else c.ink3)
        }
        Text(text, style = RowTitle.copy(fontWeight = FontWeight.Normal), color = if (enabled) c.ink2 else c.ink3)
    }
}

/** Back to the plain sidebar from a full list (Everything, all side chats). */
@Composable
internal fun BackHeader(text: String, tag: String, onBack: () -> Unit) {
    val c = MikaTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onBack)
            .padding(horizontal = 24.dp).testTagged(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DirectionalIcon(MikaIcons.Back, stringResource(R.string.back), Modifier.size(16.dp), tint = c.ink2)
        Text(text, Modifier.semantics { heading() }, style = HeaderText, color = c.ink2)
    }
}

/** A side chat nested under its channel: a thin line on the left joins them. */
@Composable
internal fun NestedRow(content: @Composable () -> Unit) {
    val c = MikaTheme.colors
    Box(
        Modifier.padding(start = 34.dp).drawBehind {
            drawLine(c.edge, Offset(0f, 0f), Offset(0f, size.height), 1.5.dp.toPx())
        }.padding(start = 2.dp),
    ) { content() }
}

/** The round compose button beside the search pill: a new side chat. */
@Composable
internal fun ComposeButton(enabled: Boolean, onClick: () -> Unit) {
    val c = MikaTheme.colors
    val label = stringResource(R.string.orbit_side_new)
    Box(
        Modifier.size(MikaSize.TouchTarget).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick).testTagged("drawer-compose"),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(44.dp).background(c.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(MikaIcons.New, label, Modifier.size(19.dp), tint = if (enabled) c.onAccent else c.onAccent.copy(alpha = 0.5f))
        }
    }
}

/** Settings and About at the foot of the sidebar: round frosted buttons. */
@Composable
internal fun FooterButton(icon: ImageVector, label: String, tag: String, dot: Boolean = false, onClick: () -> Unit) {
    val c = MikaTheme.colors
    Box(
        Modifier.size(MikaSize.TouchTarget).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick).testTagged(tag),
        contentAlignment = Alignment.Center,
    ) {
        HeaderPill(44.dp)
        Icon(icon, label, Modifier.size(20.dp), tint = c.ink2)
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(8.dp).background(c.bad, CircleShape))
    }
}

// ---------- Space rows ----------

/** When a space's page last changed, in words: "Updated 5 min ago". */
@Composable
internal fun spaceUpdated(space: Space, now: Long): String {
    val ago = DateUtils.getRelativeTimeSpanString(space.updatedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
    return if (now - space.updatedAt < DateUtils.MINUTE_IN_MILLIS) stringResource(R.string.space_updated_now)
    else stringResource(R.string.space_updated, ago)
}

/** A space in the sidebar: its tile, name, when it was updated, and a green dot that says it is live. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SpaceEntry(space: Space, now: Long, onOpen: (Space) -> Unit, onActions: (Space, Rect) -> Unit) {
    val c = MikaTheme.colors
    val haptic = LocalHapticFeedback.current
    val bounds = remember { mutableStateOf(Rect.Zero) }
    Row(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth()
            .onGloballyPositioned { bounds.value = Rect(it.positionInWindow(), it.size.toSize()) }
            .clip(NavShape)
            .combinedClickable(
                onLongClickLabel = stringResource(R.string.drawer_actions),
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onActions(space, bounds.value) },
                onClick = { onOpen(space) },
            )
            .heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp).testTagged("space-row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpaceRowContent(space, now)
    }
}

@Composable
private fun RowScope.SpaceRowContent(space: Space, now: Long) {
    val c = MikaTheme.colors
    SpaceTile(space)
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (space.pinnedAt != null) Icon(MikaIcons.Pin, stringResource(R.string.drawer_pinned_mark), Modifier.size(13.dp), tint = c.ink3)
            Text(space.name, Modifier.weight(1f, fill = false), style = RowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).background(c.ok, CircleShape))
            Text(spaceUpdated(space, now), style = RowSub, color = c.ink3, maxLines = 1)
        }
    }
}

/** Long-press on a space: pin, rename, hide, delete. */
@Composable
internal fun SpaceMenu(
    space: Space,
    bounds: Rect,
    progress: () -> Float,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MikaTheme.colors
    val now = System.currentTimeMillis()
    LongPressMenu(
        bounds, progress, pane = stringResource(R.string.drawer_actions), tag = "space-menu",
        entries = listOf(
            MenuEntry(stringResource(if (space.pinnedAt != null) R.string.unpin else R.string.pin), MikaIcons.Pin, "space-menu-pin", onClick = onPin),
            MenuEntry(stringResource(R.string.drawer_rename), MikaIcons.Edit, "space-menu-rename", onClick = onRename),
            MenuEntry(stringResource(if (space.hidden) R.string.space_show else R.string.space_hide), MikaIcons.Eye, "space-menu-hide", onClick = onHide),
            MenuEntry(stringResource(R.string.space_delete), MikaIcons.Trash, "space-menu-delete", c.bad, onDelete),
        ),
        onDismiss = onDismiss,
    ) {
        Row(
            Modifier.floatShadow(c, NavShape).background(c.surface, NavShape).clip(NavShape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) { SpaceRowContent(space, now) }
    }
}

// ---------- Creating, renaming and deleting a space ----------

/** One sentence says what the space is; the name is optional and drawn from the sentence when left empty. */
@Composable
internal fun CreateSpaceDialog(onCreate: (description: String, name: String) -> Unit, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.space_create)) },
        text = {
            KeyboardDismissArea {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.space_create_body), style = MikaType.Caption, color = MikaTheme.colors.ink2)
                    OutlinedTextField(
                        description, { if (it.length <= 600) description = it },
                        Modifier.fillMaxWidth().keepsKeyboard().testTagged("space-description"),
                        label = { Text(stringResource(R.string.space_description)) },
                        placeholder = { Text(stringResource(R.string.space_description_example)) },
                        minLines = 2, maxLines = 4,
                    )
                    OutlinedTextField(
                        name, { if (it.length <= 40) name = it },
                        Modifier.fillMaxWidth().keepsKeyboard().testTagged("space-name"),
                        label = { Text(stringResource(R.string.space_name_optional)) }, singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(description.trim(), name.trim()) }, enabled = description.isNotBlank(),
                modifier = Modifier.testTagged("space-create"),
            ) { Text(stringResource(R.string.space_create_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun RenameSpaceDialog(space: Space, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(space.id) { mutableStateOf(space.name) }
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_rename)) },
        text = {
            OutlinedTextField(name, { if (it.length <= 40) name = it }, Modifier.fillMaxWidth().keepsKeyboard(), singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun DeleteSpaceDialog(space: Space, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.space_delete)) },
        text = { Text(stringResource(R.string.space_delete_body, space.name)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.space_delete), color = MikaTheme.colors.bad) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---------- The space itself ----------

/**
 * A space opened full screen: its live page, Refresh (Orbit gathers fresh data and rewrites the page) and
 * Change it (opens the space's chat to ask for changes). The page reloads by itself when Orbit rewrites it.
 */
@Composable
internal fun SpaceScreen(
    space: Space,
    working: Boolean,
    onRefresh: () -> Unit,
    onChange: () -> Unit,
    onClose: () -> Unit,
    /** The page changed on disk while open: the sidebar's "Updated" line moves on. */
    onPageChanged: () -> Unit = {},
) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val c = MikaTheme.colors
        val context = LocalContext.current
        val dirs = remember(context) { SandboxDirs(context) }
        var page by remember(space.id) { mutableStateOf<String?>(null) }
        var loaded by remember(space.id) { mutableStateOf(false) }
        // Orbit rewrites the file as it builds or refreshes; read it again every couple of seconds while open.
        val busy by rememberUpdatedState(working)
        val changed by rememberUpdatedState(onPageChanged)
        LaunchedEffect(space.id) {
            while (true) {
                val next = SpacePanel.read(dirs, space)
                if (next != page) {
                    if (loaded && next != null) changed()
                    page = next
                }
                loaded = true
                delay(if (busy) 1_500 else 4_000)
            }
        }
        BackHandler(onBack = onClose)
        Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BarIconButton(MikaIcons.Back, stringResource(R.string.back), onClose, pill = true)
                SpaceTile(space, 28.dp)
                Column(Modifier.weight(1f)) {
                    Text(space.name, Modifier.semantics { heading() }, style = MikaType.Title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (working) stringResource(R.string.space_working) else spaceUpdated(space, System.currentTimeMillis()),
                        style = MikaType.Hint, color = c.ink3, maxLines = 1,
                    )
                }
                BarIconButton(MikaIcons.Refresh, stringResource(R.string.space_refresh), onRefresh, enabled = !working, pill = true)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(22.dp)).background(Color.Black)) {
                val html = page
                if (html == null) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SpaceTile(space, 56.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(if (loaded && !working) R.string.space_empty else R.string.space_building),
                            style = MikaType.Body, color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                } else {
                    SpacePage(html)
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                Box(
                    Modifier.clip(CircleShape).cardSurface(c, CircleShape).clickable(role = Role.Button, onClick = onChange)
                        .padding(horizontal = 22.dp, vertical = 13.dp).testTagged("space-change"),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(MikaIcons.Message, null, Modifier.size(17.dp), tint = c.accent)
                        Text(stringResource(R.string.space_change), style = MikaType.Label, color = c.ink)
                    }
                }
            }
        }
    }
}

/** The page in a WebView with scripts on and everything else off: no network, no files, no content access. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SpacePage(html: String) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                webViewClient = object : WebViewClient() {
                    // Nothing leaves the page: every request other than the page itself gets an empty answer.
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse =
                        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
                }
                tag = html
                // A fixed origin keeps localStorage (the page's ticks and notes) across reloads.
                loadDataWithBaseURL("https://space.orbit.local/", html, "text/html", "utf-8", null)
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL("https://space.orbit.local/", html, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** A test tag only when one is given. */
private fun Modifier.testTagged(tag: String): Modifier = if (tag.isEmpty()) this else this.testTag(tag)
