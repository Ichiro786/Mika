package com.wangzan101.mika.task

import com.wangzan101.mika.core.run.ChannelPeer
import com.wangzan101.mika.core.run.RunOrigin
import com.wangzan101.mika.whatsapp.WhatsappChannel

/** Unlock only for scheduled tasks or WhatsApp tasks the user allowed; the permission is read again at unlock time. */
internal fun taskUnlockAuthorization(
    origin: RunOrigin,
    peer: ChannelPeer?,
    canUnlockChannel: (ChannelPeer?) -> Boolean,
): (() -> Boolean)? = when {
    origin == RunOrigin.Trigger -> ({ true })
    origin == RunOrigin.Channel && peer?.channelId == WhatsappChannel.ID && canUnlockChannel(peer) ->
        ({ canUnlockChannel(peer) })
    else -> null
}
