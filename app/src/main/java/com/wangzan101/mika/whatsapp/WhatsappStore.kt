package com.wangzan101.mika.whatsapp

import com.wangzan101.mika.core.run.ChannelPeer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** A chat the user allowed Orbit to read and message. */
@Serializable
data class WhatsappChatRef(val jid: String, val name: String, val group: Boolean = false)

/** One message kept for an allowed chat, so Orbit can read it later. */
@Serializable
data class WhatsappLine(val id: String, val sender: String, val text: String, val time: Long, val fromMe: Boolean)

@Serializable
data class PendingWhatsappMessage(val messageId: String, val peer: ChannelPeer, val text: String, val replyToAskId: String? = null)

@Serializable
private data class WhatsappState(
    val enabled: Boolean = true,
    /** Only messages that start with "Orbit" start a task, so notes to yourself stay notes. */
    val wakeWord: Boolean = true,
    /** The linked account; a different account starts with no allowed chats and no unlock permission. */
    val ownJid: String? = null,
    val threadId: String? = null,
    val allowed: List<WhatsappChatRef> = emptyList(),
    val pending: List<PendingWhatsappMessage> = emptyList(),
    val acknowledged: List<String> = emptyList(),
    /** Ids of messages Orbit sent itself, so they never come back as tasks. */
    val sent: List<String> = emptyList(),
    val unlockFor: String? = null,
)

@Serializable
private data class WhatsappInbox(
    val lines: Map<String, List<WhatsappLine>> = emptyMap(),
    val readUpTo: Map<String, Long> = emptyMap(),
)

/**
 * Settings, the task journal and recent messages of allowed chats, written atomically on this phone.
 * The WhatsApp session itself lives in the bridge's own database next to these files.
 */
class WhatsappStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val stateFile = File(dir, "state.json")
    private val inboxFile = File(dir, "inbox.json")
    private var state = read(stateFile, WhatsappState.serializer()) ?: WhatsappState()
    private var inbox = read(inboxFile, WhatsappInbox.serializer()) ?: WhatsappInbox()
    private val _allowed = MutableStateFlow(state.allowed)
    private val _allowUnlock = MutableStateFlow(unlockAllowed())
    private val _settings = MutableStateFlow(state.enabled to state.wakeWord)
    val allowedChats: StateFlow<List<WhatsappChatRef>> = _allowed.asStateFlow()
    val allowUnlock: StateFlow<Boolean> = _allowUnlock.asStateFlow()
    /** Receiving on or off, and whether the "Orbit" wake word is required. */
    val settings: StateFlow<Pair<Boolean, Boolean>> = _settings.asStateFlow()

    @Synchronized fun ownJid(): String? = state.ownJid
    @Synchronized fun enabled() = state.enabled
    @Synchronized fun wakeWord() = state.wakeWord

    /** Called whenever the bridge connects; a different account resets everything tied to the old one. */
    @Synchronized fun linked(jid: String) {
        if (jid.isBlank() || jid == state.ownJid) return
        save(WhatsappState(enabled = state.enabled, wakeWord = state.wakeWord, ownJid = jid))
        saveInbox(WhatsappInbox())
    }

    @Synchronized fun clear() {
        save(WhatsappState(enabled = state.enabled, wakeWord = state.wakeWord))
        saveInbox(WhatsappInbox())
    }

    @Synchronized fun setEnabled(value: Boolean) = save(state.copy(enabled = value, unlockFor = state.unlockFor.takeIf { value }))
    @Synchronized fun setWakeWord(value: Boolean) = save(state.copy(wakeWord = value))

    @Synchronized fun setUnlockAllowed(value: Boolean) {
        val jid = if (value) checkNotNull(state.ownJid) { "Link WhatsApp first" } else null
        save(state.copy(unlockFor = jid))
    }

    private fun unlockAllowed() = state.unlockFor != null && state.unlockFor == state.ownJid

    /** Checked again right before each unlock, so a revoked permission or another account wins. */
    @Synchronized fun canUnlock(peer: ChannelPeer?) = peer != null && unlockAllowed() && accepts(peer)

    /** The user's own "Message yourself" chat, where tasks come in and answers go out. */
    @Synchronized fun selfPeer(): ChannelPeer? {
        val jid = state.ownJid ?: return null
        val thread = state.threadId ?: "whatsapp-${UUID.randomUUID()}".also { save(state.copy(threadId = it)) }
        return ChannelPeer(WhatsappChannel.ID, jid, thread)
    }

    @Synchronized fun accepts(peer: ChannelPeer) = state.enabled && state.ownJid != null &&
        peer.channelId == WhatsappChannel.ID && peer.peerId == state.ownJid && peer.threadId == state.threadId

    @Synchronized fun hasMessage(messageId: String) =
        messageId in state.acknowledged || state.pending.any { it.messageId == messageId }

    @Synchronized fun journal(message: PendingWhatsappMessage): Boolean {
        if (!accepts(message.peer) || hasMessage(message.messageId)) return false
        save(state.copy(pending = state.pending + message))
        return true
    }

    @Synchronized fun pending(): List<PendingWhatsappMessage> = state.pending

    @Synchronized fun acknowledge(messageId: String) {
        if (state.pending.none { it.messageId == messageId } && messageId in state.acknowledged) return
        save(state.copy(
            pending = state.pending.filterNot { it.messageId == messageId },
            acknowledged = (state.acknowledged - messageId + messageId).takeLast(MAX_IDS),
        ))
    }

    @Synchronized fun rememberSent(id: String) {
        if (id.isNotBlank()) save(state.copy(sent = (state.sent + id).takeLast(MAX_IDS)))
    }

    @Synchronized fun wasSent(id: String) = id in state.sent

    @Synchronized fun allowed(): List<WhatsappChatRef> = state.allowed
    @Synchronized fun isAllowed(jid: String) = state.allowed.any { it.jid == jid }
    @Synchronized fun chat(jid: String): WhatsappChatRef? = state.allowed.firstOrNull { it.jid == jid }

    /** Removing a chat also deletes what Orbit kept from it. */
    @Synchronized fun setAllowed(chats: List<WhatsappChatRef>) {
        val own = state.ownJid
        val next = chats.filter { it.jid != own }.distinctBy { it.jid }
        save(state.copy(allowed = next))
        val keep = next.map { it.jid }.toSet()
        saveInbox(WhatsappInbox(inbox.lines.filterKeys { it in keep }, inbox.readUpTo.filterKeys { it in keep }))
    }

    /** Keeps a message only when its chat is allowed; anything else is never written down. */
    @Synchronized fun record(jid: String, line: WhatsappLine) {
        if (!isAllowed(jid)) return
        val old = inbox.lines[jid].orEmpty()
        if (old.any { it.id == line.id }) return
        val next = (old + line).sortedBy { it.time }.takeLast(MAX_LINES)
        saveInbox(inbox.copy(lines = inbox.lines + (jid to next)))
    }

    @Synchronized fun lines(jid: String, limit: Int): List<WhatsappLine> = inbox.lines[jid].orEmpty().takeLast(limit)

    @Synchronized fun unread(jid: String): Int {
        val since = inbox.readUpTo[jid] ?: 0
        return inbox.lines[jid].orEmpty().count { !it.fromMe && it.time > since }
    }

    @Synchronized fun markRead(jid: String) {
        val newest = inbox.lines[jid]?.maxOfOrNull { it.time } ?: return
        saveInbox(inbox.copy(readUpTo = inbox.readUpTo + (jid to newest)))
    }

    private fun save(next: WhatsappState) {
        write(stateFile, json.encodeToString(WhatsappState.serializer(), next))
        state = next
        _allowed.value = next.allowed
        _allowUnlock.value = unlockAllowed()
        _settings.value = next.enabled to next.wakeWord
    }

    private fun saveInbox(next: WhatsappInbox) {
        write(inboxFile, json.encodeToString(WhatsappInbox.serializer(), next))
        inbox = next
    }

    private fun <T> read(file: File, serializer: kotlinx.serialization.KSerializer<T>): T? =
        if (file.isFile) runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull() else null

    private fun write(file: File, text: String) {
        dir.mkdirs()
        val temp = File(dir, file.name + ".tmp")
        FileOutputStream(temp).use { it.write(text.toByteArray()); it.fd.sync() }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        internal const val MAX_IDS = 512
        internal const val MAX_LINES = 50
    }
}
