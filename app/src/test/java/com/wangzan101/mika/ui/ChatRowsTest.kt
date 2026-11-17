package com.wangzan101.mika.ui

import com.wangzan101.mika.chat.SharedFile
import com.wangzan101.mika.task.SteerNote
import com.wangzan101.mika.task.SteerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatRowsTest {
    private fun count(
        exchanges: List<Exchange>,
        phoneSetupNeeded: Boolean = false,
        hasBlocked: Boolean = false,
    ) = chatItemCount(exchanges, kept = null, phoneSetupNeeded, hasBlocked)

    @Test
    fun emptyAndBlockedStatesHaveTheirActualRows() {
        assertEquals(1, count(emptyList()))
        assertEquals(1, count(emptyList(), hasBlocked = true))
        assertEquals(1, count(emptyList(), phoneSetupNeeded = true, hasBlocked = true))
    }

    @Test
    fun appendedTurnAdvancesTheTailBeforeLazyListMeasuresIt() {
        val previous = List(30) { Exchange("问题 $it", emptyList(), ExchangeStatus.Done("回答 $it", null), live = false) }
        val oldTail = count(previous) - 1
        val sent = previous + Exchange("刚发送的新一轮", emptyList(), ExchangeStatus.Working, live = true)
        // 新一轮是用户气泡、工作区、状态行三项。这个目标只依赖当前状态,与旧布局里有几项无关。
        assertEquals(oldTail + 3, count(sent) - 1)
    }

    @Test
    fun streamingAnswerAndOptionalRowsStayAlignedWithTheDsl() {
        val working = Exchange("问", emptyList(), ExchangeStatus.Working, live = true, draft = "正在回答")
        assertEquals(3, count(listOf(working)))
        val done = working.copy(status = ExchangeStatus.Done("回答", null), draft = null, runId = "run")
        assertEquals(3, count(listOf(done)))
        assertEquals(4, count(listOf(done), phoneSetupNeeded = true))
        assertEquals(5, count(listOf(done), phoneSetupNeeded = true, hasBlocked = true))
    }

    @Test
    fun keysListRowsInTheSameOrderTheDslEmitsThem() {
        val working = Exchange("问", emptyList(), ExchangeStatus.Working, live = true, draft = "正在回答")
        assertEquals(listOf("u0", "d0", "r0"), chatItemKeys(listOf(working), kept = null, phoneSetupNeeded = false, hasBlocked = false))

        val steer = SteerNote("s1", "改成靠窗", SteerState.Joined, interrupted = "被打断", finished = "正在改")
        val done = Exchange(
            "买票", emptyList(), ExchangeStatus.Done("完成", null), live = false, runId = "run",
            steers = listOf(steer),
            files = listOf(SharedFile("a.txt", 1L, "a.txt", "content://a")),
        )
        assertEquals(
            listOf("u0", "s0", "a0-s1", "i0-s1", "m0-s1", "r0", "f0"),
            chatItemKeys(listOf(done), kept = null, phoneSetupNeeded = false, hasBlocked = false),
        )
    }

    @Test
    fun targetKeysPointAtTheRenderedRow() {
        val steer = SteerNote("s1", "改成靠窗", SteerState.Joined, interrupted = "被打断", finished = "正在改")
        val done = Exchange(
            "买票", emptyList(), ExchangeStatus.Done("完成", null), live = false, runId = "run",
            steers = listOf(steer),
            files = listOf(SharedFile("a.txt", 1L, "a.txt", "content://a")),
        )
        val list = listOf(done)
        assertEquals("u0", chatTargetKey(list, ChatTarget.Message(0, null)))
        assertEquals("m0-s1", chatTargetKey(list, ChatTarget.Message(0, 0)))
        assertEquals("a0-s1", chatTargetKey(list, ChatTarget.Reply(0, 0, interrupted = false)))
        assertEquals("i0-s1", chatTargetKey(list, ChatTarget.Reply(0, 0, interrupted = true)))
        assertEquals("f0", chatTargetKey(list, ChatTarget.Files(0)))
        assertEquals("r0", chatTargetKey(list, ChatTarget.Status(0)))
        assertNull(chatTargetKey(list, ChatTarget.Message(1, null)))
    }

    @Test
    fun withdrawnSteerFallsBackToTheTurnStart() {
        val withdrawn = SteerNote("s1", "撤回的话", SteerState.Withdrawn)
        val done = Exchange("问", emptyList(), ExchangeStatus.Done("答", null), live = false, steers = listOf(withdrawn))
        assertEquals("u0", chatTargetKey(listOf(done), ChatTarget.Message(0, 0)))
    }

    @Test
    fun repliesCountOnlyWhatOrbitSaid() {
        val asked = Exchange("问", emptyList(), ExchangeStatus.Working, live = true)
        assertEquals(0, chatReplyCount(listOf(asked)))
        val streaming = asked.copy(draft = "正在回答")
        assertEquals(1, chatReplyCount(listOf(streaming)))
        // A streamed reply that finishes is still the same one reply.
        val done = asked.copy(status = ExchangeStatus.Done("回答", null))
        assertEquals(1, chatReplyCount(listOf(done)))
        assertEquals(0, chatReplyCount(listOf(asked.copy(status = ExchangeStatus.Cancelled()))))
        assertEquals(1, chatReplyCount(listOf(asked.copy(status = ExchangeStatus.Cancelled(summary = "停在第二步")))))
        // What the user adds mid-task is not a reply; a reply already said before it is.
        val steered = done.copy(steers = listOf(
            SteerNote("s1", "改成靠窗", SteerState.Joined, finished = "已经在改"),
            SteerNote("s2", "撤回的话", SteerState.Withdrawn, finished = "不算"),
        ))
        assertEquals(2, chatReplyCount(listOf(steered)))
        assertEquals(3, chatReplyCount(listOf(done, steered)))
    }

    @Test
    fun unreadNeverGoesNegativeAndTheBadgeCapsAtNine() {
        assertEquals(2, unreadSince(seen = 3, now = 5))
        assertEquals(0, unreadSince(seen = 5, now = 5))
        assertEquals(0, unreadSince(seen = 5, now = 4))
        assertEquals("1", unreadBadge(1))
        assertEquals("9", unreadBadge(9))
        assertEquals("9+", unreadBadge(10))
    }

    @Test
    fun replyBubbleLeavesAGutterOnTheRight() {
        assertEquals(0.85f, AnswerBubbleWidth)
        assertEquals(850, answerBubbleMaxWidth(1000))
        assertEquals(918, answerBubbleMaxWidth(1080))
    }

    private fun keys(ex: Exchange, operatingPhone: Boolean = false) =
        chatItemKeys(listOf(ex), kept = null, phoneSetupNeeded = false, hasBlocked = false, operatingPhone = operatingPhone)

    /** While a task works there is exactly one "still going" row, never the working block and the Thinking line together. */
    @Test
    fun neverTwoStatusRowsAtOnce() {
        val step = StepRow(1, "打开应用", "", StepState.Ok)
        val shapes = buildList {
            for (waiting in listOf(false, true)) for (steps in listOf(emptyList(), listOf(step))) for (draft in listOf(null, "正在回答")) {
                add(Exchange("问", steps, ExchangeStatus.Working, live = true, waitingForModel = waiting, draft = draft))
            }
        }
        for (ex in shapes) for (phone in listOf(false, true)) {
            val rows = statusRows(ex, backgroundNote = phone)
            assertEquals("$ex phone=$phone", false, rows.work && rows.thinking)
            // Working with no reply text yet always shows one of the two.
            if (ex.draft == null) assertEquals("$ex phone=$phone", true, rows.work || rows.thinking)
        }
        // The case from the bug report: step 2, waiting on the model, operating the phone.
        val between = Exchange("问", listOf(step, step), ExchangeStatus.Working, live = true, waitingForModel = true)
        assertEquals(StatusRows(work = true, thinking = false, typing = false), statusRows(between, backgroundNote = true))
        assertEquals(listOf("u0", "s0", "r0"), keys(between, operatingPhone = true))
        // Before the first step, nothing streamed: only the Thinking line.
        val first = Exchange("问", emptyList(), ExchangeStatus.Working, live = true, waitingForModel = true)
        assertEquals(listOf("u0", "th0", "r0"), keys(first))
        // Operating the phone, the working block (with its background note) takes its place.
        assertEquals(listOf("u0", "s0", "r0"), keys(first, operatingPhone = true))
    }

    @Test
    fun typingDotsAndSteersAreCountedWhereTheListPutsThem() {
        val streaming = Exchange("问", emptyList(), ExchangeStatus.Working, live = true, waitingForModel = true, draft = "正在回答")
        assertEquals(listOf("u0", "d0", "t0", "r0"), keys(streaming))
        // A message the model has not taken yet sits after the growing reply, one it has taken sits before.
        val joined = SteerNote("s1", "改成靠窗", SteerState.Joined)
        val pending = SteerNote("s2", "再快一点", SteerState.Pending)
        val steered = streaming.copy(steers = listOf(joined, pending))
        assertEquals(listOf("u0", "m0-s1", "d0", "t0", "m0-s2", "r0"), keys(steered))
    }

    @Test
    fun everyKeyInOneConversationIsUnique() {
        val step = StepRow(1, "打开应用", "", StepState.Ok)
        val all = listOf(
            Exchange("一", listOf(step), ExchangeStatus.Done("好", null), live = false, runId = "r1"),
            Exchange("二", emptyList(), ExchangeStatus.Working, live = true, waitingForModel = true),
            Exchange("三", listOf(step), ExchangeStatus.Working, live = true, waitingForModel = true, draft = "写着"),
        )
        val keys = chatItemKeys(all, kept = null, phoneSetupNeeded = true, hasBlocked = false, operatingPhone = true)
        assertEquals(keys.size, keys.toSet().size)
    }
}
