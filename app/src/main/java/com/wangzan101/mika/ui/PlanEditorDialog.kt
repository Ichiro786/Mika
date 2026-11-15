package com.wangzan101.mika.ui

import androidx.compose.foundation.layout.Arrangement
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.ui.components.OrbitFieldShape
import com.wangzan101.mika.ui.components.OrbitTextField
import com.wangzan101.mika.ui.theme.MikaIcons
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.core.trigger.ConditionNotify
import com.wangzan101.mika.core.trigger.PlanDraft
import com.wangzan101.mika.core.trigger.PlanRepeat
import com.wangzan101.mika.core.trigger.PlanValidation
import com.wangzan101.mika.core.trigger.PlanValidationException
import com.wangzan101.mika.core.trigger.TriggerDefinition
import com.wangzan101.mika.core.trigger.editedTrigger
import com.wangzan101.mika.core.trigger.formatPlanEditorTime
import com.wangzan101.mika.core.trigger.newPlanDraft
import com.wangzan101.mika.core.trigger.toPlanDraft
import com.wangzan101.mika.core.trigger.upcoming
import com.wangzan101.mika.i18n.tr
import com.wangzan101.mika.i18n.uiLocale
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.KeyboardDismissArea
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.components.SegmentedControl
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.UUID

/** 新建和编辑共用一份草稿；预览不另猜时间，直接显示将要保存的计划。 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorDialog(
    original: TriggerDefinition?,
    newThreadId: String,
    onDismiss: () -> Unit,
    onSave: (TriggerDefinition) -> Unit,
) {
    val newId = remember(original?.id) { original?.id ?: UUID.randomUUID().toString() }
    var draft by remember(original?.id) {
        mutableStateOf(original?.toPlanDraft() ?: newPlanDraft(System.currentTimeMillis()))
    }
    var attempted by remember(original?.id) { mutableStateOf(false) }
    val candidate = runCatching {
        editedTrigger(draft, original, newId, newThreadId, System.currentTimeMillis())
    }
    val preview = candidate.getOrNull()

    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTimeFor by remember { mutableStateOf<LocalDate?>(null) }
    var addingClock by remember { mutableStateOf(false) }
    val zone = runCatching { ZoneId.of(draft.zone.trim()) }.getOrElse { ZoneId.systemDefault() }
    val firstAt = runCatching { LocalDateTime.parse(draft.firstAt.trim(), PICK_TIME) }.getOrNull()
        ?: LocalDateTime.now(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
    val c = MikaTheme.colors

    // A sheet over the current page instead of a cramped dialog; it rides above the keyboard.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = c.bg,
        contentWindowInsets = { WindowInsets.ime.union(WindowInsets.navigationBars) },
        modifier = Modifier.testTag("plan-editor"),
    ) {
        KeyboardDismissArea {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr(if (original == null) R.string.plans_create_title else R.string.plans_edit_title),
                        Modifier.weight(1f).semantics { heading() },
                        style = MikaType.Title.copy(fontSize = 22.sp, lineHeight = 28.sp), color = c.ink,
                    )
                    TextButton(onClick = onDismiss) { Text(tr(R.string.cancel), color = c.ink2) }
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    SegmentedControl(
                        listOf(tr(R.string.plans_task_mode), tr(R.string.plans_reminder_mode)),
                        if (draft.reminder) 1 else 0,
                        onSelect = { selected ->
                            draft = draft.copy(
                                reminder = selected == 1,
                                condition = if (selected == 1) "" else draft.condition,
                                cooldownMinutes = if (selected == 1) "" else draft.cooldownMinutes,
                            )
                        },
                        tags = listOf("plan-editor-task", "plan-editor-reminder"),
                    )
                    Spacer(Modifier.height(14.dp))
                    PlanField(
                        draft.content,
                        { draft = draft.copy(content = it) },
                        tr(if (draft.reminder) R.string.plans_content_reminder else R.string.plans_content_task),
                        "plan-editor-content",
                        singleLine = false,
                    )
                    Spacer(Modifier.height(14.dp))
                    SegmentedControl(
                        listOf(
                            tr(R.string.plans_once), tr(R.string.plans_repeat_interval),
                            tr(R.string.plans_every_day), tr(R.string.plans_repeat_weekly),
                        ),
                        draft.repeat.ordinal,
                        onSelect = { selected ->
                            val repeat = PlanRepeat.entries[selected]
                            val today = runCatching { ZoneId.of(draft.zone) }.getOrElse { ZoneId.systemDefault() }
                                .let { Instant.now().atZone(it).dayOfWeek.value }
                            draft = draft.copy(
                                repeat = repeat,
                                weekdays = if (repeat == PlanRepeat.Weekly && draft.weekdays.isEmpty()) setOf(today)
                                else draft.weekdays,
                            )
                        },
                        tags = listOf("plan-editor-once", "plan-editor-interval", "plan-editor-daily", "plan-editor-weekly"),
                    )
                    Spacer(Modifier.height(14.dp))
                    if (draft.repeat in setOf(PlanRepeat.Once, PlanRepeat.Interval)) {
                        PlanField(
                            draft.firstAt, { draft = draft.copy(firstAt = it) },
                            tr(R.string.plans_edit_label), "plan-editor-first-at",
                            trailing = { PickButton(tr(R.string.orbit_plan_pick_time)) { pickingDate = true } },
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    if (draft.repeat == PlanRepeat.Interval) {
                        PlanField(
                            draft.intervalMinutes, { draft = draft.copy(intervalMinutes = it) },
                            tr(R.string.plans_interval_minutes), "plan-editor-interval-minutes", KeyboardType.Number,
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    if (draft.repeat in setOf(PlanRepeat.Daily, PlanRepeat.Weekly)) {
                        PlanField(
                            draft.clock, { draft = draft.copy(clock = it) },
                            tr(R.string.plans_clock), "plan-editor-clock",
                            placeholder = "08:00, 12:30, 20:00",
                            trailing = { PickButton(tr(R.string.orbit_plan_add_time)) { addingClock = true } },
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    if (draft.repeat == PlanRepeat.Weekly) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..7).forEach { day ->
                                MikaButton(
                                    DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, uiLocale()),
                                    onClick = {
                                        draft = draft.copy(weekdays = draft.weekdays.toMutableSet().apply {
                                            if (!add(day)) remove(day)
                                        })
                                    },
                                    modifier = Modifier.testTag("plan-editor-weekday-$day"),
                                    kind = if (day in draft.weekdays) ButtonKind.Primary else ButtonKind.Tonal,
                                    size = ButtonSize.Small,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    ZoneRow(draft.zone) { draft = draft.copy(zone = it) }
                    if (!draft.reminder) {
                        Spacer(Modifier.height(14.dp))
                        PlanField(
                            draft.condition, { draft = draft.copy(condition = it) },
                            tr(R.string.plans_condition_optional), "plan-editor-condition", singleLine = false,
                        )
                        if (draft.condition.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            SegmentedControl(
                                listOf(tr(R.string.plans_notify_change), tr(R.string.plans_notify_each)),
                                if (draft.notifyWhen == ConditionNotify.BecomesMet) 0 else 1,
                                onSelect = {
                                    draft = draft.copy(notifyWhen = if (it == 0) ConditionNotify.BecomesMet else ConditionNotify.EachTimeMet)
                                },
                                tags = listOf("plan-editor-notify-change", "plan-editor-notify-each"),
                            )
                            Spacer(Modifier.height(14.dp))
                            PlanField(
                                draft.cooldownMinutes, { draft = draft.copy(cooldownMinutes = it) },
                                tr(R.string.plans_cooldown_minutes), "plan-editor-cooldown", KeyboardType.Number,
                            )
                        }
                    }
                    PlanSwitch(tr(R.string.plans_when_idle), draft.whenIdle, { draft = draft.copy(whenIdle = it) }, "plan-editor-idle")
                    PlanSwitch(tr(R.string.plans_enabled), draft.enabled, { draft = draft.copy(enabled = it) }, "plan-editor-enabled")

                    preview?.let { plan ->
                        Spacer(Modifier.height(8.dp))
                        Text(tr(R.string.plans_preview), style = MikaType.Caption, color = MikaTheme.colors.ink3)
                        val zone = plan.calendarZone ?: draft.zone
                        upcoming(plan, System.currentTimeMillis(), 3).forEachIndexed { index, at ->
                            Text(
                                "${index + 1}  ${formatPlanEditorTime(at, zone)} ($zone)",
                                Modifier.testTag("plan-editor-preview-$index"),
                                style = MikaType.Caption,
                                color = MikaTheme.colors.ink3,
                            )
                        }
                    }
                    if (attempted && preview == null) {
                        val reason = (candidate.exceptionOrNull() as? PlanValidationException)?.reason
                        Spacer(Modifier.height(8.dp))
                        Text(
                            reason?.let { planValidationText(it) } ?: tr(R.string.op_failed),
                            Modifier.testTag("plan-editor-error"),
                            style = MikaType.Caption,
                            color = MikaTheme.colors.bad,
                        )
                    }
                }
                MikaButton(
                    tr(R.string.save),
                    onClick = {
                        val saved = runCatching {
                            editedTrigger(draft, original, newId, newThreadId, System.currentTimeMillis())
                        }
                        if (saved.isFailure) attempted = true
                        else onSave(saved.getOrThrow())
                    },
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
                        .testTag("plan-editor-save"),
                )
            }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = firstAt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickingDate = false
                    pickingTimeFor = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        ?: firstAt.toLocalDate()
                }) { Text(tr(R.string.done)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(tr(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
    pickingTimeFor?.let { date ->
        TimeDialog(firstAt.hour, firstAt.minute, DateFormat.is24HourFormat(context), onDismiss = { pickingTimeFor = null }) { h, m ->
            draft = draft.copy(firstAt = PICK_TIME.format(LocalDateTime.of(date, LocalTime.of(h, m))))
            pickingTimeFor = null
        }
    }
    if (addingClock) {
        TimeDialog(9, 0, DateFormat.is24HourFormat(context), onDismiss = { addingClock = false }) { h, m ->
            val time = CLOCK.format(LocalTime.of(h, m))
            draft = draft.copy(clock = if (draft.clock.isBlank()) time else "${draft.clock.trimEnd().trimEnd(',')}, $time")
            addingClock = false
        }
    }
}

private val PICK_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Material time picker in a dialog; the result is written back as text, so typing still works. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, is24Hour: Boolean, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(hour, minute, is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MikaTheme.colors.surface,
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute) }) { Text(tr(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.cancel)) } },
        text = { TimePicker(state) },
    )
}

/** Small round button at the end of a field that opens a picker. */
@Composable
private fun PickButton(label: String, onClick: () -> Unit) {
    val c = MikaTheme.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(c.surface)
            .semantics { contentDescription = label }
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(MikaIcons.Clock, null, Modifier.size(18.dp), tint = c.ink2) }
}

/** The time zone stays out of the way: one line with the current zone, opened into a field on request. */
@Composable
private fun ZoneRow(zone: String, onChange: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val c = MikaTheme.colors
    if (editing) {
        PlanField(zone, onChange, tr(R.string.plans_zone), "plan-editor-zone")
    } else {
        Row(
            Modifier.fillMaxWidth().clip(OrbitFieldShape).clickable(role = Role.Button) { editing = true }
                .heightIn(min = 48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(MikaIcons.Globe, null, Modifier.size(18.dp), tint = c.ink3)
            Text(zone, Modifier.weight(1f).testTag("plan-editor-zone-value"), style = MikaType.Body, color = c.ink2, maxLines = 1)
            Text(tr(R.string.orbit_plan_zone_change), style = MikaType.Label, color = c.accent)
        }
    }
}

@Composable
private fun PlanField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    placeholder: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    OrbitTextField(
        value, onValueChange, label,
        Modifier.testTag(tag),
        placeholder = placeholder,
        singleLine = singleLine,
        keyboardType = keyboardType,
        trailing = trailing,
    )
}

@Composable
private fun PlanSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MikaType.Body, color = MikaTheme.colors.ink2)
        MikaSwitch(checked, onChange, Modifier.testTag(tag))
    }
}

@Composable
private fun planValidationText(reason: PlanValidation): String = tr(when (reason) {
    PlanValidation.MissingContent -> R.string.plans_error_content
    PlanValidation.ContentTooLong -> R.string.plans_error_content_long
    PlanValidation.ConditionTooLong -> R.string.plans_error_condition_long
    PlanValidation.ReminderWithCondition -> R.string.plans_error_reminder_condition
    PlanValidation.InvalidFirstTime -> R.string.plans_error_time
    PlanValidation.TimeNotFuture -> R.string.plans_need_future
    PlanValidation.InvalidInterval -> R.string.plans_error_interval
    PlanValidation.InvalidClock -> R.string.plans_error_clock
    PlanValidation.InvalidZone -> R.string.plans_error_zone
    PlanValidation.InvalidWeekdays -> R.string.plans_error_weekdays
    PlanValidation.InvalidCooldown -> R.string.plans_error_cooldown
})
