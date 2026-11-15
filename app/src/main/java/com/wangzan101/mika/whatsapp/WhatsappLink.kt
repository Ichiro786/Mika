package com.wangzan101.mika.whatsapp

import java.io.File

/** The linked-device connection, kept behind an interface so the rest of the code never loads the native library in tests. */
interface WhatsappLink {
    interface Events {
        fun state(state: String, detail: String)
        fun qr(code: String)
        fun message(json: String)
    }

    fun open(dir: File, events: Events)
    fun linked(): Boolean
    fun connected(): Boolean
    /** The account's own address, such as 15551234567@s.whatsapp.net, or "" before linking. */
    fun ownJid(): String
    /** Returns the 8-character code the user types in WhatsApp › Linked devices › Link with phone number instead. */
    fun linkWithPhone(phone: String): String
    fun linkWithQr()
    fun cancelLink()
    fun disconnect()
    fun reconnect()
    fun unlink()
    /** Returns the new message's id. */
    fun sendText(chat: String, text: String): String
    /** Contacts and groups as JSON, for the chat picker. */
    fun chats(): String
}

/** whatsmeow, built from whatsapp-bridge/ with gomobile. */
class GoWhatsappLink : WhatsappLink {
    override fun open(dir: File, events: WhatsappLink.Events) = wabridge.Wabridge.open(dir.path, object : wabridge.Listener {
        override fun onState(state: String, detail: String) = events.state(state, detail)
        override fun onQr(code: String) = events.qr(code)
        override fun onMessage(message: String) = events.message(message)
    })
    override fun linked() = wabridge.Wabridge.isLinked()
    override fun connected() = wabridge.Wabridge.isConnected()
    override fun ownJid(): String = wabridge.Wabridge.ownJID()
    override fun linkWithPhone(phone: String): String = wabridge.Wabridge.linkWithPhone(phone)
    override fun linkWithQr() = wabridge.Wabridge.linkWithQr()
    override fun cancelLink() = wabridge.Wabridge.cancelLink()
    override fun disconnect() = wabridge.Wabridge.disconnect()
    override fun reconnect() = wabridge.Wabridge.reconnect()
    override fun unlink() = wabridge.Wabridge.unlink()
    override fun sendText(chat: String, text: String): String = wabridge.Wabridge.sendText(chat, text)
    override fun chats(): String = wabridge.Wabridge.chats()
}
