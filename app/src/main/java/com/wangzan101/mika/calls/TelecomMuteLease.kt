package com.wangzan101.mika.calls

/** Main-thread policy only. Runtime binds the lease to a single Call and its InCallService. */
internal class TelecomMuteLease private constructor(private val originalMuted: Boolean) {
    enum class Release {
        PRESERVE, VERIFY_OVERRIDE, REQUEST_UNMUTE, VERIFY_UNMUTED;

        fun confirmed(sameCall: Boolean, telecomMuted: Boolean?, hardwareMuted: Boolean?): Boolean = when (this) {
            PRESERVE, VERIFY_OVERRIDE -> !sameCall || telecomMuted != null && telecomMuted == hardwareMuted
            REQUEST_UNMUTE, VERIFY_UNMUTED -> hardwareMuted == false && (!sameCall || telecomMuted == false)
        }
    }

    val needsMuteRequest: Boolean get() = !originalMuted
    private var muteRequested = false
    private var telecomAcknowledged = originalMuted
    private var relinquished = false
    private var releasing = false

    fun markMuteRequested() {
        check(needsMuteRequest && !muteRequested && !releasing)
        muteRequested = true
    }

    /** False after an acknowledged mute means Telecom or the user took back control. */
    fun observeTelecomMute(muted: Boolean): Boolean {
        if (releasing || relinquished) return false
        if (muted) {
            telecomAcknowledged = true
        } else if (telecomAcknowledged) {
            relinquished = true
            return true
        }
        return false
    }

    fun isConfirmed(telecomMuted: Boolean?, hardwareMuted: Boolean?): Boolean =
        !releasing && !relinquished && telecomAcknowledged && telecomMuted == true && hardwareMuted == true

    fun beginRelease(sameCall: Boolean): Release {
        val alreadyReleasing = releasing
        releasing = true
        return when {
            relinquished -> Release.VERIFY_OVERRIDE
            !muteRequested -> Release.PRESERVE
            sameCall && !alreadyReleasing -> Release.REQUEST_UNMUTE
            else -> Release.VERIFY_UNMUTED
        }
    }

    companion object {
        fun fromBaseline(telecomMuted: Boolean?, hardwareMuted: Boolean?): TelecomMuteLease? =
            telecomMuted?.takeIf { it == hardwareMuted }?.let(::TelecomMuteLease)
    }
}

/** Android 14+ sends the legacy audio state on bind, then has a dedicated mute callback. */
internal class TelecomMuteState(private val dedicatedMuteCallback: Boolean) {
    enum class Source { LEGACY_AUDIO, MUTE_CALLBACK }
    enum class Update { CHANGED, DUPLICATE, IGNORED_LEGACY }

    var value: Boolean? = null
        private set

    fun update(source: Source, muted: Boolean): Update {
        // A later route/audio snapshot is not authoritative for mute on Android 14+.
        if (dedicatedMuteCallback && source == Source.LEGACY_AUDIO && value != null) return Update.IGNORED_LEGACY
        if (value == muted) return Update.DUPLICATE
        value = muted
        return Update.CHANGED
    }

    fun reset() {
        value = null
    }
}

/** An allowlist maps known shell outcomes to fixed diagnostics; no raw shell string leaves here. */
internal fun shellAudioStopDiagnostic(reason: String?): String? = when (val code = reason?.trim()) {
    "Handset microphone was unmuted." -> "Device microphone was unmuted; the AI handed the call back"
    "Cellular audio mode ended.", "Cellular audio ended." -> "Cellular call audio mode ended; the system is handling the call"
    "Downlink consumer stopped draining audio." -> "Cellular downlink audio is stalled; the system is handling the call"
    "Android silenced the cellular audio capture." -> "Android stopped providing call audio to the AI; handed back to the system"
    "Uplink playback stopped accepting audio." -> "Cellular uplink audio playback made no progress; the system is handling the call"
    "Helper released.", "Call audio owner died." -> "The built-in helper released call audio; the system is handling the call"
    else -> when {
        code == null -> null
        code.startsWith("Downlink read failed (") -> "Cannot read cellular downlink audio; the system is handling the call"
        code.startsWith("Uplink write failed (") -> "Cannot write cellular uplink audio; the system is handling the call"
        code.startsWith("Uplink flush failed:") -> "Cannot flush cellular uplink audio; the system is handling the call"
        code.startsWith("Uplink did not route to the telephony device (") ||
            code.startsWith("Uplink unexpectedly routed to device type ") -> "Uplink audio is not routed to the cellular call; the system is handling the call"
        code.startsWith("Downlink unexpectedly routed to device type ") -> "Downlink audio is not routed to the cellular call; the system is handling the call"
        else -> null
    }
}
