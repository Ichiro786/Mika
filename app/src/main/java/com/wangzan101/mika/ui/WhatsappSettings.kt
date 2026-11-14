package com.wangzan101.mika.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.components.MikaDialog
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.components.OrbitTextField
import com.wangzan101.mika.ui.components.PageScaffold
import com.wangzan101.mika.ui.components.PillTone
import com.wangzan101.mika.ui.components.StateCard
import com.wangzan101.mika.ui.components.StatusPill
import com.wangzan101.mika.ui.components.TileTone
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.whatsapp.WhatsappChatRef
import com.wangzan101.mika.whatsapp.WhatsappRuntime
import com.wangzan101.mika.whatsapp.WhatsappStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The status pill on the Settings home row. */
@Composable
internal fun WhatsappEntryStatus(status: WhatsappStatus) {
    val (text, tone) = when (status) {
        WhatsappStatus.Connected -> R.string.whatsapp_status_connected to PillTone.Ok
        WhatsappStatus.Connecting, is WhatsappStatus.Linking -> R.string.whatsapp_status_connecting to PillTone.Accent
        WhatsappStatus.Paused -> R.string.whatsapp_status_paused to PillTone.Neutral
        WhatsappStatus.Offline -> R.string.whatsapp_status_offline to PillTone.Neutral
        WhatsappStatus.NotLinked -> R.string.whatsapp_status_not_linked to PillTone.Neutral
        WhatsappStatus.LoggedOut -> R.string.whatsapp_status_logged_out to PillTone.Warn
        WhatsappStatus.Banned -> R.string.whatsapp_status_banned to PillTone.Warn
        WhatsappStatus.Outdated -> R.string.whatsapp_status_outdated to PillTone.Warn
        is WhatsappStatus.Failed -> R.string.whatsapp_status_failed to PillTone.Warn
    }
    StatusPill(stringResource(text), tone)
}

/** Settings › WhatsApp: link as a device, pick the chats Orbit may use, pause or unlink. */
@Composable
fun WhatsappSettingsScreen(runtime: WhatsappRuntime, onBack: () -> Unit) {
    val status by runtime.status.collectAsState()
    val allowed by runtime.store.allowedChats.collectAsState()
    val allowUnlock by runtime.store.allowUnlock.collectAsState()
    val settings by runtime.store.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var linked by remember { mutableStateOf(runtime.linked()) }
    var phone by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var confirmUnlink by remember { mutableStateOf(false) }
    val linkFailed = stringResource(R.string.whatsapp_link_failed)
    val actionFailed = stringResource(R.string.whatsapp_action_failed)

    LaunchedEffect(status) { linked = runtime.linked() }

    fun act(failure: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = failure
            } finally {
                busy = false
            }
        }
    }

    if (picking) {
        WhatsappChatPicker(runtime, allowed, onDone = { picking = false })
        return
    }

    PageScaffold(stringResource(R.string.whatsapp_title), { runtime.cancelLink(); onBack() }) {
        val linking = status as? WhatsappStatus.Linking
        val (title, detail, tone) = whatsappStateCopy(status, linked)
        StateCard(MikaIcons.Chats, title, detail, tone, Modifier.testTag("whatsapp-status"))

        GroupLabel(stringResource(R.string.whatsapp_how))
        GroupCard {
            Text(
                stringResource(R.string.whatsapp_how_body), Modifier.padding(16.dp),
                style = MikaType.Body, color = MikaTheme.colors.ink2,
            )
        }

        if (!linked) {
            GroupLabel(stringResource(R.string.whatsapp_risk_title))
            GroupCard {
                Text(
                    stringResource(R.string.whatsapp_risk_body), Modifier.padding(16.dp).testTag("whatsapp-risk"),
                    style = MikaType.Body, color = MikaTheme.colors.ink2,
                )
            }
        }

        when {
            linking?.code != null -> MikaCard(Modifier.padding(bottom = 12.dp).testTag("whatsapp-code")) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.whatsapp_code_title), style = MikaType.BodyStrong, color = MikaTheme.colors.ink)
                    SelectionContainer {
                        Text(
                            linking.code.chunked(4).joinToString("-"), Modifier.padding(vertical = 16.dp),
                            style = MikaType.Title.copy(fontFamily = FontFamily.Monospace, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 3.sp),
                            color = MikaTheme.colors.ink,
                        )
                    }
                    Text(
                        stringResource(R.string.whatsapp_code_help), style = MikaType.Caption,
                        color = MikaTheme.colors.ink2, textAlign = TextAlign.Center,
                    )
                    MikaButton(stringResource(R.string.cancel), runtime::cancelLink,
                        Modifier.fillMaxWidth().padding(top = 8.dp), kind = ButtonKind.Text)
                }
            }
            linking?.qr != null -> MikaCard(Modifier.padding(bottom = 12.dp).testTag("whatsapp-qr")) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCodeImage(linking.qr, stringResource(R.string.whatsapp_qr_help), Modifier.padding(vertical = 8.dp).size(240.dp))
                    Text(
                        stringResource(R.string.whatsapp_qr_help), style = MikaType.Caption,
                        color = MikaTheme.colors.ink2, textAlign = TextAlign.Center,
                    )
                    MikaButton(stringResource(R.string.cancel), runtime::cancelLink,
                        Modifier.fillMaxWidth().padding(top = 8.dp), kind = ButtonKind.Text)
                }
            }
            !linked -> {
                OrbitTextField(
                    phone, { value -> phone = value.filter { it.isDigit() || it == '+' || it == ' ' } },
                    stringResource(R.string.whatsapp_phone_label), Modifier.testTag("whatsapp-phone"),
                    placeholder = "+1 555 123 4567", keyboardType = KeyboardType.Phone,
                )
                MikaButton(
                    stringResource(R.string.whatsapp_get_code),
                    { act(linkFailed) { runtime.linkWithPhone(phone) } },
                    Modifier.fillMaxWidth().padding(top = 12.dp).testTag("whatsapp-get-code"),
                    enabled = !busy && phone.count { it.isDigit() } >= 7,
                )
                MikaButton(
                    stringResource(R.string.whatsapp_use_qr), { act(linkFailed) { runtime.linkWithQr() } },
                    Modifier.fillMaxWidth(), kind = ButtonKind.Text, enabled = !busy,
                )
            }
        }
        error?.let { FooterNote(it, color = MikaTheme.colors.bad) }

        if (linked && linking == null) {
            GroupLabel(stringResource(R.string.whatsapp_chats))
            GroupCard {
                if (allowed.isEmpty()) {
                    Text(
                        stringResource(R.string.whatsapp_chats_none), Modifier.padding(16.dp),
                        style = MikaType.Body, color = MikaTheme.colors.ink2,
                    )
                } else allowed.forEachIndexed { i, chat ->
                    ListRow(if (chat.group) MikaIcons.Chats else MikaIcons.Message, chat.name, first = i == 0,
                        detail = if (chat.group) stringResource(R.string.whatsapp_group) else null)
                }
            }
            MikaButton(
                stringResource(R.string.whatsapp_chats_pick), { picking = true },
                Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("whatsapp-pick"), kind = ButtonKind.Secondary,
                enabled = status == WhatsappStatus.Connected,
            )

            GroupLabel(stringResource(R.string.whatsapp_connection))
            GroupCard {
                val (enabled, wakeWord) = settings
                ListRow(
                    MikaIcons.Power, stringResource(R.string.whatsapp_receive), stringResource(R.string.whatsapp_receive_sub),
                    first = true, onClick = if (busy) null else ({
                        act(actionFailed) { withContext(Dispatchers.IO) { runtime.setEnabled(!enabled) } }
                    }),
                ) { MikaSwitch(enabled, null, Modifier.testTag("whatsapp-enabled"), enabled = !busy) }
                val wakeLabel = stringResource(R.string.whatsapp_wake_word)
                ListRow(MikaIcons.Spark, wakeLabel, stringResource(R.string.whatsapp_wake_word_sub)) {
                    MikaSwitch(wakeWord, { value ->
                        act(actionFailed) { withContext(Dispatchers.IO) { runtime.store.setWakeWord(value) } }
                    }, Modifier.semantics { contentDescription = wakeLabel }.testTag("whatsapp-wake"), enabled = !busy)
                }
                val unlockLabel = stringResource(R.string.whatsapp_allow_unlock)
                ListRow(MikaIcons.Key, unlockLabel, stringResource(R.string.whatsapp_allow_unlock_sub)) {
                    MikaSwitch(allowUnlock, { value ->
                        act(actionFailed) { withContext(Dispatchers.IO) { runtime.store.setUnlockAllowed(value) } }
                    }, Modifier.semantics { contentDescription = unlockLabel }.testTag("whatsapp-allow-unlock"), enabled = !busy)
                }
            }
            FooterNote(stringResource(
                R.string.whatsapp_allow_unlock_foot,
                stringResource(R.string.mcp_title),
                stringResource(R.string.mcp_advanced),
                stringResource(R.string.device_access_title),
            ))
            TextButton({ confirmUnlink = true }, Modifier.fillMaxWidth().testTag("whatsapp-unlink"), enabled = !busy) {
                Text(stringResource(R.string.whatsapp_unlink), color = MikaTheme.colors.bad)
            }
        }
        FooterNote(stringResource(R.string.whatsapp_footer))
    }

    if (confirmUnlink) {
        MikaDialog(
            onDismissRequest = { confirmUnlink = false },
            title = { Text(stringResource(R.string.whatsapp_unlink_title)) },
            text = { Text(stringResource(R.string.whatsapp_unlink_body)) },
            confirmButton = {
                TextButton({
                    confirmUnlink = false
                    act(actionFailed) {
                        runtime.unlink()
                        linked = false
                    }
                }) { Text(stringResource(R.string.whatsapp_unlink), color = MikaTheme.colors.bad) }
            },
            dismissButton = { TextButton({ confirmUnlink = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Picks the chats Orbit may read and message; nothing is allowed until the user ticks a chat here. */
@Composable
private fun WhatsappChatPicker(runtime: WhatsappRuntime, allowed: List<WhatsappChatRef>, onDone: () -> Unit) {
    val chosen = remember { mutableStateListOf<WhatsappChatRef>().apply { addAll(allowed) } }
    var all by remember { mutableStateOf<List<WhatsappChatRef>?>(null) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { all = runCatching { runtime.pickableChats() }.getOrDefault(emptyList()) }
    fun finish() {
        val picked = chosen.toList()
        scope.launch { withContext(Dispatchers.IO) { runtime.setAllowed(picked) } }
        onDone()
    }
    PageScaffold(stringResource(R.string.whatsapp_chats_pick), ::finish) {
        OrbitTextField(query, { query = it }, stringResource(R.string.whatsapp_chats_search), Modifier.testTag("whatsapp-search"))
        val list = all
        GroupCard(Modifier.padding(top = 12.dp)) {
            when {
                list == null -> Text("…", Modifier.padding(16.dp), color = MikaTheme.colors.ink2)
                list.isEmpty() -> Text(
                    stringResource(R.string.whatsapp_chats_empty), Modifier.padding(16.dp),
                    style = MikaType.Body, color = MikaTheme.colors.ink2,
                )
                else -> {
                    val shown = (chosen + list.filter { c -> chosen.none { it.jid == c.jid } })
                        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                        .take(MAX_SHOWN)
                    shown.forEachIndexed { i, chat ->
                        val on = chosen.any { it.jid == chat.jid }
                        val toggle = { if (on) chosen.removeAll { it.jid == chat.jid } else chosen.add(chat) }
                        ListRow(
                            if (chat.group) MikaIcons.Chats else MikaIcons.Message, chat.name, first = i == 0,
                            detail = if (chat.group) stringResource(R.string.whatsapp_group) else null,
                            onClick = { toggle() },
                        ) { MikaSwitch(on, { toggle() }, Modifier.semantics { contentDescription = chat.name }) }
                    }
                }
            }
        }
        MikaButton(stringResource(R.string.whatsapp_chats_done), ::finish, Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

private const val MAX_SHOWN = 200

@Composable
private fun whatsappStateCopy(status: WhatsappStatus, linked: Boolean): Triple<String, String, TileTone> {
    fun t(title: Int, detail: Int, tone: TileTone) = Triple(title, detail, tone)
    val (title, detail, tone) = when (status) {
        WhatsappStatus.Connected -> t(R.string.whatsapp_status_connected, R.string.whatsapp_detail_connected, TileTone.Ok)
        WhatsappStatus.Connecting -> t(R.string.whatsapp_status_connecting, R.string.whatsapp_detail_connecting, TileTone.Accent)
        is WhatsappStatus.Linking -> t(R.string.whatsapp_status_linking, R.string.whatsapp_detail_connecting, TileTone.Accent)
        WhatsappStatus.Offline -> if (linked) t(R.string.whatsapp_status_offline, R.string.whatsapp_detail_offline, TileTone.Neutral)
            else t(R.string.whatsapp_status_not_linked, R.string.whatsapp_detail_not_linked, TileTone.Neutral)
        WhatsappStatus.Paused -> t(R.string.whatsapp_status_paused, R.string.whatsapp_detail_paused, TileTone.Neutral)
        WhatsappStatus.NotLinked -> t(R.string.whatsapp_status_not_linked, R.string.whatsapp_detail_not_linked, TileTone.Neutral)
        WhatsappStatus.LoggedOut -> t(R.string.whatsapp_status_logged_out, R.string.whatsapp_detail_logged_out, TileTone.Warn)
        WhatsappStatus.Banned -> t(R.string.whatsapp_status_banned, R.string.whatsapp_detail_banned, TileTone.Bad)
        WhatsappStatus.Outdated -> t(R.string.whatsapp_status_outdated, R.string.whatsapp_detail_outdated, TileTone.Warn)
        is WhatsappStatus.Failed -> t(R.string.whatsapp_status_failed, R.string.whatsapp_detail_failed, TileTone.Bad)
    }
    return Triple(stringResource(title), stringResource(detail), tone)
}
