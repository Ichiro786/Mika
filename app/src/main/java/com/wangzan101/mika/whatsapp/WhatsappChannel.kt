package com.wangzan101.mika.whatsapp

import com.wangzan101.mika.core.run.Channel
import com.wangzan101.mika.core.run.ChannelInput
import com.wangzan101.mika.core.run.ChannelInputSink
import com.wangzan101.mika.core.run.ChannelPeer
import com.wangzan101.mika.core.run.Delivery
import com.wangzan101.mika.core.run.SendOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Result of one send through the linked device. */
sealed interface WhatsappSend {
    data class Sent(val id: String) : WhatsappSend
    /** Not connected, so nothing went out. */
    data object NotSent : WhatsappSend
    /** WhatsApp gave no clear answer; the message may or may not have gone out. */
    data object Unknown : WhatsappSend
}

/**
 * Tasks through the user's own "Message yourself" chat: their messages there start or answer a task,
 * and Orbit's questions and results go back to the same chat. Owner checks, question routing and
 * task runs stay in the shared channel layer.
 */
class WhatsappChannel(
    private val store: WhatsappStore,
    private val sender: suspend (chat: String, text: String) -> WhatsappSend,
    private val activeAsk: (String) -> Delivery.Ask? = { null },
    private val fileText: (Delivery.Image) -> String = { "Saved ${it.name} on your phone. Open Orbit to see it." },
) : Channel {
    override val id = ID
    override val confirms = false
    @Volatile private var sink: ChannelInputSink? = null
    private val receiveLock = Mutex()
    private val sendLock = Mutex()

    override fun isAlive(peer: ChannelPeer) = store.accepts(peer)

    override fun start(sink: ChannelInputSink): AutoCloseable {
        this.sink = sink
        return AutoCloseable { if (this.sink === sink) this.sink = null }
    }

    override fun acknowledge(messageId: String) = store.acknowledge(messageId)

    override suspend fun send(peer: ChannelPeer, delivery: Delivery): SendOutcome {
        if (peer.channelId != id || !isAlive(peer)) return SendOutcome.Failed
        val text = when (delivery) {
            is Delivery.Ask -> delivery.text
            is Delivery.Result -> delivery.text
            is Delivery.Notice -> delivery.text
            is Delivery.Progress -> delivery.text
            is Delivery.Image -> fileText(delivery)
        }
        return sendLock.withLock {
            val result = try {
                sender(peer.peerId, "$REPLY_PREFIX$text")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                WhatsappSend.Unknown
            }
            when (result) {
                is WhatsappSend.Sent -> { store.rememberSent(result.id); SendOutcome.Delivered }
                WhatsappSend.NotSent -> SendOutcome.Failed
                WhatsappSend.Unknown -> SendOutcome.Uncertain
            }
        }
    }

    /** A message the user wrote in their own chat. Orbit's own replies are filtered out by id. */
    suspend fun receive(messageId: String, text: String) = receiveLock.withLock {
        val input = sink ?: return@withLock
        if (!store.enabled() || store.wasSent(messageId) || store.hasMessage(messageId)) return@withLock
        val peer = store.selfPeer() ?: return@withLock
        val askId = activeAsk(peer.threadId)?.takeIf { it.kind == Delivery.Ask.Kind.Input }?.askId
        // An answer to Orbit's open question needs no wake word; a new task does, unless the user turned it off.
        val body = when {
            askId != null -> stripWakeWord(text) ?: text.trim()
            store.wakeWord() -> stripWakeWord(text) ?: return@withLock
            else -> stripWakeWord(text) ?: text.trim()
        }
        if (body.isBlank()) return@withLock
        val pending = PendingWhatsappMessage(messageId, peer, body, askId)
        if (store.journal(pending)) dispatch(pending, input)
    }

    /** Hands messages saved before a restart to the task layer again; the task registry ignores repeats. */
    suspend fun replayPending() = receiveLock.withLock {
        val input = sink ?: return@withLock
        store.pending().forEach { if (store.accepts(it.peer)) dispatch(it, input) }
    }

    private suspend fun dispatch(pending: PendingWhatsappMessage, input: ChannelInputSink) {
        input.receive(ChannelInput.Text(pending.peer, pending.messageId, pending.text, pending.replyToAskId))
    }

    companion object {
        const val ID = "whatsapp"
        /** Marks Orbit's replies in a chat where both sides are the user. */
        const val REPLY_PREFIX = "Orbit: "
        private val WAKE = Regex("""^\s*orbit\b[\s,:.!-]*""", RegexOption.IGNORE_CASE)

        /** The message without a leading "Orbit", or null when it does not start with it. */
        internal fun stripWakeWord(text: String): String? =
            WAKE.find(text)?.let { text.substring(it.range.last + 1).trim() }
    }
}
