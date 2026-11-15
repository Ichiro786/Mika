package com.wangzan101.mika.whatsapp

import com.wangzan101.mika.core.whatsapp.WhatsappPort
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The agent's view of WhatsApp: only the chats the user allowed, and sends only after the tool's confirmation. */
class AndroidWhatsappPort(private val runtime: WhatsappRuntime) : WhatsappPort {
    private val store get() = runtime.store

    override suspend fun chats(): JsonObject = buildJsonObject {
        putJsonArray("chats") {
            store.allowed().forEach { chat ->
                addJsonObject {
                    put("chat", chat.jid)
                    put("name", chat.name)
                    put("group", chat.group)
                    put("unread", store.unread(chat.jid))
                }
            }
        }
        put("connected", runtime.status.value == WhatsappStatus.Connected)
    }

    override suspend fun read(chat: String, limit: Int): JsonObject {
        val ref = store.chat(chat) ?: return notAllowed()
        val lines = store.lines(chat, limit)
        store.markRead(chat)
        return buildJsonObject {
            put("chat", ref.jid)
            put("name", ref.name)
            putJsonArray("messages") {
                lines.forEach { line ->
                    addJsonObject {
                        put("from", line.sender)
                        put("fromMe", line.fromMe)
                        put("time", java.time.Instant.ofEpochMilli(line.time).toString())
                        put("text", line.text)
                    }
                }
            }
            if (lines.isEmpty()) put("note", "No messages kept yet; only messages that arrive while WhatsApp is linked are available.")
        }
    }

    override suspend fun send(chat: String, text: String): JsonObject {
        val ref = store.chat(chat) ?: return notAllowed()
        return when (val result = runtime.sendNow(ref.jid, text)) {
            is WhatsappSend.Sent -> {
                store.record(ref.jid, WhatsappLine(result.id, runtime.youLabel(), text, System.currentTimeMillis(), fromMe = true))
                buildJsonObject { put("sent", true); put("chat", ref.name) }
            }
            WhatsappSend.NotSent -> buildJsonObject { put("sent", false); put("error", "WhatsApp is not connected; nothing was sent.") }
            WhatsappSend.Unknown -> buildJsonObject {
                put("sent", "uncertain")
                put("error", "WhatsApp did not confirm. Read the chat before trying again.")
            }
        }
    }

    override fun chatName(chat: String): String? = store.chat(chat)?.name

    private fun notAllowed() = buildJsonObject {
        put("error", "chat_not_allowed")
        putJsonArray("allowed") { store.allowed().forEach { add(it.jid) } }
    }
}
