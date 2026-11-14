package com.wangzan101.mika.whatsapp

import android.content.Context
import android.content.Intent
import android.util.Log
import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.R
import com.wangzan101.mika.core.action.ConfirmChoice
import com.wangzan101.mika.core.run.ChannelGateway
import com.wangzan101.mika.core.run.ChannelPeer
import com.wangzan101.mika.core.run.MemoryOwnerIdentity
import com.wangzan101.mika.core.run.RunId
import com.wangzan101.mika.i18n.tr
import com.wangzan101.mika.settings.FeatureFlags
import com.wangzan101.mika.task.TaskService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

sealed interface WhatsappStatus {
    data object NotLinked : WhatsappStatus
    /** Waiting for the user to enter [code] in WhatsApp, or to scan [qr]. */
    data class Linking(val code: String? = null, val qr: String? = null) : WhatsappStatus
    data object Connecting : WhatsappStatus
    data object Connected : WhatsappStatus
    data object Offline : WhatsappStatus
    data object Paused : WhatsappStatus
    /** The phone removed Orbit from Linked devices, or another session replaced it. */
    data object LoggedOut : WhatsappStatus
    data object Banned : WhatsappStatus
    data object Outdated : WhatsappStatus
    data class Failed(val detail: String) : WhatsappStatus
}

@Serializable
private data class BridgeMessage(
    val id: String = "", val chat: String = "", val chatName: String = "", val sender: String = "",
    val senderName: String = "", val text: String = "", val time: Long = 0, val fromMe: Boolean = false,
    val group: Boolean = false, val self: Boolean = false,
)

@Serializable
private data class BridgeChat(val jid: String = "", val name: String = "", val group: Boolean = false, val self: Boolean = false)

/**
 * WhatsApp as a linked device: one account, tasks through the user's own chat, and reading or
 * messaging only the chats the user allowed. A foreground service keeps the connection open; tasks
 * still run in TaskService.
 */
class WhatsappRuntime(
    private val context: Context,
    val store: WhatsappStore,
    private val sessionDir: File,
    private val link: WhatsappLink = GoWhatsappLink(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val owners = MemoryOwnerIdentity()
    private val app get() = context.applicationContext as MikaApp
    private val gateway = ChannelGateway(owners,
        { threadId -> app.taskState.run(threadId)?.activeAsk },
        object : ChannelGateway.Route {
            override suspend fun newTask(peer: ChannelPeer, messageId: String, text: String) =
                TaskService.startChannel(context, peer, messageId, text)

            override suspend fun answer(threadId: String, askId: String, text: String): Boolean =
                app.taskState.run(threadId)?.answerFromChannel(text, askId) == true

            override suspend fun confirm(threadId: String, askId: String, choice: ConfirmChoice) = false
            override suspend fun stop(runId: RunId) = TaskService.cancel(context, runId.value)
            override suspend fun cancelAsk(threadId: String) {
                val run = app.taskState.run(threadId) ?: return
                run.activeAsk?.askId?.let(run::cancelAsk)
            }
        })

    val channel = WhatsappChannel(store, ::sendNow,
        activeAsk = { threadId -> app.taskState.run(threadId)?.activeAsk },
        fileText = { tr(R.string.whatsapp_file_saved, it.name) })
    val port = AndroidWhatsappPort(this)

    private val _status = MutableStateFlow<WhatsappStatus>(
        if (store.ownJid() == null) WhatsappStatus.NotLinked else WhatsappStatus.Offline)
    val status: StateFlow<WhatsappStatus> = _status.asStateFlow()
    private var registration: AutoCloseable? = null
    @Volatile private var opened = false

    private val events = object : WhatsappLink.Events {
        override fun state(state: String, detail: String) = onState(state, detail)
        override fun qr(code: String) {
            if (_status.value is WhatsappStatus.Linking) _status.value = WhatsappStatus.Linking(qr = code)
        }
        override fun message(json: String) { scope.launch { onMessage(json) } }
    }

    fun channelFor(id: String) = channel.takeIf { id == WhatsappChannel.ID }
    fun linked() = store.ownJid() != null

    /** Tools are offered only while linked and at least one chat is allowed. */
    fun toolsAvailable() = linked() && store.enabled() && store.allowed().isNotEmpty()

    @Synchronized private fun openLink(): Boolean {
        if (opened) return true
        return try {
            link.open(sessionDir, events)
            opened = true
            true
        } catch (e: Throwable) {
            Log.w(TAG, "WhatsApp library could not start", e)
            _status.value = WhatsappStatus.Failed(e.message ?: e::class.simpleName.orEmpty())
            false
        }
    }

    /** Called by [WhatsappService]; false means the service should stop. */
    @Synchronized fun sync(): Boolean {
        if (FeatureFlags.get(context).quitRequested() || store.ownJid() == null) return false
        if (!openLink()) return false
        if (!link.linked()) {
            store.clear()
            _status.value = WhatsappStatus.LoggedOut
            return false
        }
        if (!store.enabled()) {
            _status.value = WhatsappStatus.Paused
            return false
        }
        if (registration == null) {
            store.selfPeer()?.let { owners.bind(it) }
            registration = gateway.start(channel)
            scope.launch { channel.replayPending() }
        }
        if (!link.connected()) {
            _status.value = WhatsappStatus.Connecting
            link.reconnect()
        } else _status.value = WhatsappStatus.Connected
        return true
    }

    @Synchronized fun stop() {
        registration?.close()
        registration = null
        if (opened) runCatching { link.disconnect() }
        _status.value = when {
            store.ownJid() == null -> WhatsappStatus.NotLinked
            !store.enabled() -> WhatsappStatus.Paused
            else -> WhatsappStatus.Offline
        }
    }

    fun ensureRunning() {
        if (FeatureFlags.get(context).quitRequested() || !store.enabled() || store.ownJid() == null) return
        runCatching { context.startForegroundService(Intent(context, WhatsappService::class.java)) }
            .onFailure { _status.value = WhatsappStatus.Failed(it.message ?: it::class.simpleName.orEmpty()) }
    }

    fun setEnabled(enabled: Boolean) {
        store.setEnabled(enabled)
        if (enabled) ensureRunning() else {
            context.stopService(Intent(context, WhatsappService::class.java))
            stop()
        }
    }

    /** Starts linking by phone number and returns the code the user types in WhatsApp. */
    suspend fun linkWithPhone(phone: String): String = withContext(Dispatchers.IO) {
        check(openLink()) { "WhatsApp library could not start" }
        _status.value = WhatsappStatus.Linking()
        link.linkWithPhone(phone).also { _status.value = WhatsappStatus.Linking(code = it) }
    }

    suspend fun linkWithQr() = withContext(Dispatchers.IO) {
        check(openLink()) { "WhatsApp library could not start" }
        _status.value = WhatsappStatus.Linking()
        link.linkWithQr()
    }

    fun cancelLink() {
        if (opened) runCatching { link.cancelLink() }
        if (_status.value is WhatsappStatus.Linking || _status.value is WhatsappStatus.Failed) {
            _status.value = if (linked()) WhatsappStatus.Offline else WhatsappStatus.NotLinked
        }
    }

    /** Removes Orbit from Linked devices and forgets the session, allowed chats and kept messages. */
    suspend fun unlink() = withContext(Dispatchers.IO) {
        context.stopService(Intent(context, WhatsappService::class.java))
        store.selfPeer()?.let { owners.unbind(it) }
        stop()
        if (opened) link.unlink()
        opened = false
        store.clear()
        _status.value = WhatsappStatus.NotLinked
    }

    /** Contacts and groups for the chat picker; the user's own chat is left out. */
    suspend fun pickableChats(): List<WhatsappChatRef> = withContext(Dispatchers.IO) {
        if (!openLink()) return@withContext emptyList()
        json.decodeFromString<List<BridgeChat>>(link.chats())
            .filter { !it.self && it.jid.isNotBlank() && it.name.isNotBlank() }
            .map { WhatsappChatRef(it.jid, it.name, it.group) }
    }

    fun setAllowed(chats: List<WhatsappChatRef>) = store.setAllowed(chats)

    /** Waits up to a minute for the connection, then sends; a send never goes out while offline. */
    suspend fun sendNow(chat: String, text: String): WhatsappSend {
        if (store.ownJid() == null || !store.enabled()) return WhatsappSend.NotSent
        if (_status.value != WhatsappStatus.Connected) ensureRunning()
        withTimeoutOrNull(CONNECT_WAIT_MS) { status.first { it == WhatsappStatus.Connected } } ?: return WhatsappSend.NotSent
        return withContext(Dispatchers.IO) {
            try {
                WhatsappSend.Sent(link.sendText(chat, text))
            } catch (e: Exception) {
                Log.w(TAG, "WhatsApp send did not confirm: ${e.message}")
                WhatsappSend.Unknown
            }
        }
    }

    private fun onState(state: String, detail: String) {
        val current = _status.value
        when (state) {
            "connecting" -> if (current !is WhatsappStatus.Linking) _status.value = WhatsappStatus.Connecting
            "paired" -> _status.value = WhatsappStatus.Connecting
            "connected" -> {
                store.linked(link.ownJid())
                _status.value = WhatsappStatus.Connected
                if (registration == null) ensureRunning()
            }
            "disconnected" -> if (current == WhatsappStatus.Connected || current == WhatsappStatus.Connecting) {
                _status.value = if (store.enabled()) WhatsappStatus.Offline else WhatsappStatus.Paused
            }
            "logged_out" -> scope.launch {
                runCatching { unlink() }
                _status.value = WhatsappStatus.LoggedOut
            }
            "banned" -> _status.value = WhatsappStatus.Banned
            "outdated" -> _status.value = WhatsappStatus.Outdated
            "failed" -> _status.value = WhatsappStatus.Failed(detail)
        }
    }

    private suspend fun onMessage(raw: String) {
        val m = runCatching { json.decodeFromString<BridgeMessage>(raw) }.getOrNull() ?: return
        if (m.id.isBlank() || m.text.isBlank()) return
        if (m.self) {
            if (m.fromMe && registration != null) channel.receive(m.id, m.text)
            return
        }
        // Chats the user did not allow are dropped here, before anything is written.
        if (!store.isAllowed(m.chat)) return
        val sender = when {
            m.fromMe -> youLabel()
            m.group -> m.senderName.ifBlank { m.sender.substringBefore('@') }
            else -> store.chat(m.chat)?.name ?: m.chatName.ifBlank { m.sender.substringBefore('@') }
        }
        store.record(m.chat, WhatsappLine(m.id, sender, m.text, m.time, m.fromMe))
    }

    fun youLabel() = tr(R.string.whatsapp_you)

    companion object {
        private const val TAG = "OrbitWhatsapp"
        private const val CONNECT_WAIT_MS = 60_000L
    }
}
