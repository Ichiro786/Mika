package com.wangzan101.mika.ui

import com.wangzan101.mika.cap.web.KeptPage
import com.wangzan101.mika.task.SteerNote
import com.wangzan101.mika.task.SteerState

/**
 * 当前 UI 状态会生成的行 key,按 LazyColumn 里的顺序。自动滚到末尾和“跳到某一轮的消息”都用这一份,
 * 不再各自数一遍。条件和 ChatScreen.exchange 及其附属行一一对应;末尾状态行即使暂时没有可见内容也占一个 LazyList item。
 */
internal fun chatItemKeys(
    exchanges: List<Exchange>,
    kept: KeptPage?,
    phoneSetupNeeded: Boolean,
    hasBlocked: Boolean,
    /** The last exchange is operating the phone in the background (it then always shows its working block). */
    operatingPhone: Boolean = false,
): List<String> = buildList {
    if (exchanges.isEmpty() && !hasBlocked) add("empty")
    exchanges.forEachIndexed { index, ex ->
        if (ex.goal.isNotEmpty()) add("u$index")
        val rows = statusRows(ex, backgroundNote = operatingPhone && index == exchanges.lastIndex)
        if (rows.work) add("s$index")
        val (joined, waiting) = steersAroundDraft(ex.steers)
        fun steer(note: SteerNote) {
            if (!note.finished.isNullOrBlank()) add("a$index-${note.id}")
            if (!note.interrupted.isNullOrBlank()) add("i$index-${note.id}")
            add("m$index-${note.id}")
        }
        joined.forEach(::steer)
        if (!ex.draft.isNullOrBlank()) add("d$index")
        if (rows.typing) add("t$index")
        if (rows.thinking) add("th$index")
        waiting.forEach(::steer)
        add("r$index")
        if (KeptRow.of(ex, kept) != null) add("k$index")
        if (ex.files.isNotEmpty()) add("f$index")
        if (ex.runId != null && (ex.memoryNotes.isNotEmpty() || ex.skillNotes.isNotEmpty())) add("m$index")
    }
    if (phoneSetupNeeded && exchanges.isNotEmpty()) add("phone-setup")
    if (hasBlocked) add("blocked")
}

/**
 * Which status rows one exchange shows. The list in ChatScreen and the row model above both ask this, so they
 * cannot disagree, and at most one of them says "still going" at a time:
 * - [work]: the working block (spinner, what it is doing, timer) while the task runs, or the finished line after it.
 * - [thinking]: the plain "Thinking" line, only before anything else exists (no step, no text, not operating the phone).
 * - [typing]: the three dots under a reply that is still streaming.
 */
internal data class StatusRows(val work: Boolean, val thinking: Boolean, val typing: Boolean)

internal fun statusRows(ex: Exchange, backgroundNote: Boolean): StatusRows {
    val active = ex.status.isActive()
    val hasDraft = !ex.draft.isNullOrBlank()
    val answering = ex.status == ExchangeStatus.Working && hasDraft
    val onlyThinking = ex.status == ExchangeStatus.Working && ex.waitingForModel && ex.steps.isEmpty() && !backgroundNote && !hasDraft
    return StatusRows(
        work = if (active) !answering && !onlyThinking else ex.steps.isNotEmpty() || ex.runId != null,
        thinking = onlyThinking,
        typing = hasDraft && active && ex.waitingForModel,
    )
}

/**
 * 当前 UI 状态会在 LazyColumn 里生成多少行。自动滚到末尾必须用这份当前状态算目标,不能读上一帧的
 * LazyListLayoutInfo.totalItemsCount:发送后的副作用可能先于新行测量执行,旧行数会把新轮次留在视口外。
 */
internal fun chatItemCount(
    exchanges: List<Exchange>,
    kept: KeptPage?,
    phoneSetupNeeded: Boolean,
    hasBlocked: Boolean,
    operatingPhone: Boolean = false,
): Int = chatItemKeys(exchanges, kept, phoneSetupNeeded, hasBlocked, operatingPhone).size

/** 目标消息在 [chatItemKeys] 里的 key;轮次或消息已经不在了时返回 null。 */
internal fun chatTargetKey(exchanges: List<Exchange>, target: ChatTarget): String? {
    val turn = target.turnIndex
    val ex = exchanges.getOrNull(turn) ?: return null
    return when (target) {
        is ChatTarget.Message -> {
            val note = target.steerIndex?.let { visibleSteer(ex, it) }
            if (target.steerIndex != null) {
                if (note != null) "m$turn-${note.id}" else turnStartKey(turn, ex)
            } else {
                turnStartKey(turn, ex)
            }
        }
        is ChatTarget.Reply -> {
            val note = visibleSteer(ex, target.steerIndex)
            val key = when {
                note == null -> null
                target.interrupted && !note.interrupted.isNullOrBlank() -> "i$turn-${note.id}"
                !target.interrupted && !note.finished.isNullOrBlank() -> "a$turn-${note.id}"
                else -> null
            }
            key ?: turnStartKey(turn, ex)
        }
        is ChatTarget.Files -> if (ex.files.isNotEmpty()) "f$turn" else turnStartKey(turn, ex)
        is ChatTarget.Status -> "r$turn"
    }
}

/** 撤回的插话不在聊天页里显示,按位置找时要跳过。 */
private fun visibleSteer(ex: Exchange, index: Int): SteerNote? =
    ex.steers.filterNot { it.state == SteerState.Withdrawn }.getOrNull(index)

/** 这一轮的开头:有用户气泡就跳到气泡,否则退回结果卡。 */
private fun turnStartKey(turnIndex: Int, ex: Exchange): String =
    if (ex.goal.isNotEmpty()) "u$turnIndex" else "r$turnIndex"

private val ChatTarget.turnIndex: Int
    get() = when (this) {
        is ChatTarget.Message -> this.turnIndex
        is ChatTarget.Reply -> this.turnIndex
        is ChatTarget.Files -> this.turnIndex
        is ChatTarget.Status -> this.turnIndex
    }

/**
 * How many replies from Orbit the conversation holds: each reply that has text (finished, stopped with a summary,
 * or still streaming) and each one that was already said before the user added to a running task. What the user
 * says, progress rows, cards and file rows do not count. The jump-to-latest button shows what arrived while the
 * user was scrolled up as the difference between two of these.
 */
internal fun chatReplyCount(exchanges: List<Exchange>): Int = exchanges.sumOf { ex ->
    val finishedEarlier = ex.steers.count { it.state != SteerState.Withdrawn && !it.finished.isNullOrBlank() }
    val reply = when (val status = ex.status) {
        is ExchangeStatus.Done -> 1
        is ExchangeStatus.Cancelled -> if (status.summary.isNullOrBlank()) 0 else 1
        else -> if (ex.draft.isNullOrBlank()) 0 else 1
    }
    finishedEarlier + reply
}

/** Replies that arrived since [seen] were counted; never negative (a deleted turn is not "minus one new"). */
internal fun unreadSince(seen: Int, now: Int): Int = (now - seen).coerceAtLeast(0)

/** The count shown on the jump-to-latest badge: the number itself up to nine, then "9+". */
internal fun unreadBadge(unread: Int): String = if (unread > 9) "9+" else unread.toString()
