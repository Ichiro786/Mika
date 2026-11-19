package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.i18n.tr
import com.wangzan101.mika.task.QueuedFollowUp
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.ui.theme.MikaSpace
import com.wangzan101.mika.ui.theme.MikaRadius

/** 任务进行中补充消息的去向说明;按当前任务状态换一句,让人知道引导什么时候生效。 */
fun followUpHint(mode: FollowUpMode, status: ExchangeStatus?): String = when {
    mode == FollowUpMode.Queue -> tr(R.string.follow_up_queue_hint)
    status is ExchangeStatus.Paused || status is ExchangeStatus.WaitingUnlock ||
        status is ExchangeStatus.NeedsTakeover || status is ExchangeStatus.NeedsConfirmation ->
        tr(R.string.follow_up_steer_hint_paused)
    status is ExchangeStatus.Queued -> tr(R.string.follow_up_steer_hint_queued)
    else -> tr(R.string.follow_up_steer_hint)
}

/** 「引导当前任务 / 排队下一件」二选一;只在任务进行中显示在输入栏上方。 */
@Composable
fun FollowUpModeBar(mode: FollowUpMode, onMode: (FollowUpMode) -> Unit, status: ExchangeStatus?) {
    val c = MikaTheme.colors
    Column(Modifier.fillMaxWidth().padding(bottom = MikaSpace.S8)) {
        Row(Modifier.testTag("follow-up-mode"), horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8)) {
            ModePill(FollowUpMode.Steer, mode, onMode, tr(R.string.follow_up_steer))
            ModePill(FollowUpMode.Queue, mode, onMode, tr(R.string.follow_up_queue))
        }
        Text(followUpHint(mode, status), Modifier.padding(start = MikaSpace.S4, top = MikaSpace.S4), style = MikaType.Hint, color = c.ink3)
    }
}

@Composable
private fun ModePill(value: FollowUpMode, selected: FollowUpMode, onMode: (FollowUpMode) -> Unit, label: String) {
    val c = MikaTheme.colors
    val on = value == selected
    Box(
        Modifier.heightIn(min = 30.dp).clip(MikaShapes.Pill)
            .testTag(if (value == FollowUpMode.Steer) "follow-up-mode-steer" else "follow-up-mode-queue")
            .background(if (on) c.accent else c.fill)
            .clickable(role = Role.Tab, onClickLabel = label) { onMode(value) }
            .semantics { this.selected = on; contentDescription = label }
            .padding(horizontal = MikaSpace.S12),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MikaType.Hint, color = if (on) c.onAccent else c.ink2, maxLines = 1)
    }
}

/**
 * 输入栏上方的排队条:只含用户明确排队的项(引导 fallback 由穿插话那行「下一件」表示)。
 * 每行可以取回编辑或取消;当前没有任务在跑时还能「现在开始」,依次处理。
 */
@Composable
fun FollowUpTray(
    items: List<QueuedFollowUp>,
    /** 这个对话还有任务在跑或在等回答;为 false 时这些排队项要等开始任务才轮到。 */
    active: Boolean,
    onEdit: (String) -> Unit,
    onCancel: (String) -> Unit,
    onStartNow: () -> Unit,
) {
    if (items.isEmpty()) return
    val c = MikaTheme.colors
    Column(
        Modifier.fillMaxWidth().testTag("follow-up-tray").heightIn(max = 180.dp)
            .verticalScroll(rememberScrollState()).padding(bottom = MikaSpace.S8),
        verticalArrangement = Arrangement.spacedBy(MikaSpace.S4),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = MikaSpace.S4, end = MikaSpace.S4), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr(R.string.follow_up_count, items.size) + " · " + (if (active) tr(R.string.follow_up_queue_hint) else tr(R.string.follow_up_idle_hint)),
                Modifier.weight(1f), style = MikaType.Hint, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!active) {
                Text(
                    tr(R.string.follow_up_start_now), Modifier.clip(MikaShapes.Pill)
                        .clickable(role = Role.Button, onClick = onStartNow).padding(horizontal = MikaSpace.S8, vertical = MikaSpace.S4),
                    style = MikaType.Hint, color = c.accent,
                )
            }
        }
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(MikaRadius.Md)).background(c.fill)
                    .padding(start = MikaSpace.S12, end = MikaSpace.S4, top = MikaSpace.S4, bottom = MikaSpace.S4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8),
            ) {
                Icon(MikaIcons.Clock, null, Modifier.size(15.dp), tint = c.ink3)
                Text(
                    ChatCopy.plainTitle(item.text), Modifier.weight(1f), style = MikaType.Caption, color = c.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                TrayAction(tr(R.string.follow_up_edit), enabled = editableSteer(item.text), tag = "follow-up-edit") { onEdit(item.id) }
                TrayAction(tr(R.string.follow_up_cancel), enabled = true, tag = "follow-up-cancel") { onCancel(item.id) }
            }
        }
    }
}

@Composable
private fun TrayAction(text: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val c = MikaTheme.colors
    Text(
        text, Modifier.testTag(tag).clip(MikaShapes.Pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = MikaSpace.S8, vertical = MikaSpace.S4),
        style = MikaType.Hint, color = if (enabled) c.ink2 else c.ink3.copy(alpha = 0.5f), maxLines = 1,
    )
}
