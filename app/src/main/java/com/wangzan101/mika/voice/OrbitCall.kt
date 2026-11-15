package com.wangzan101.mika.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.wangzan101.mika.ui.Exchange
import com.wangzan101.mika.ui.ExchangeStatus
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Where a call with Orbit is: what the call screen shows and what TalkBack announces. */
sealed interface CallPhase {
    data object Connecting : CallPhase
    /** [level] 0..1 for the voice ring; [heard] is the words so far. */
    data class Listening(val level: Float = 0f, val heard: String = "") : CallPhase
    data class Thinking(val said: String) : CallPhase
    data class Speaking(val text: String) : CallPhase
    /** The run waits on something that has to be done on screen (an approval, a takeover). */
    data class NeedsScreen(val what: String) : CallPhase
    data object Ended : CallPhase
    data class Failed(val why: SpeechFailure) : CallPhase
}

/**
 * Decides when the caller has finished a sentence from the microphone level alone, so a call needs no
 * button: after some speech, a quiet gap of [quietMs] ends the turn. Engines that end on their own are fine too.
 */
class EndOfSpeech(
    private val speakLevel: Float = 0.14f,
    private val quietLevel: Float = 0.07f,
    private val minSpeechMs: Long = 250,
    private val quietMs: Long = 1_200,
) {
    private var speechSince = -1L
    private var spoke = false
    private var quietSince = -1L

    /** Feed one level reading at time [atMs]; true once the turn should end. */
    fun feed(level: Float, atMs: Long): Boolean {
        if (level >= speakLevel) {
            if (speechSince < 0) speechSince = atMs
            if (atMs - speechSince >= minSpeechMs) spoke = true
            quietSince = -1
        } else if (level < quietLevel) {
            if (speechSince >= 0 && !spoke) speechSince = -1
            if (spoke) {
                if (quietSince < 0) quietSince = atMs
                if (atMs - quietSince >= quietMs) return true
            }
        }
        return false
    }
}

/** Reads a reply aloud. Android's own text to speech by default; a test passes its own. */
interface CallSpeaker {
    suspend fun speak(text: String)
    fun stop()
    fun close()
}

/** What a markdown reply sounds like read aloud: no code, no link targets, no list or heading marks. */
fun spokenText(markdown: String, max: Int = 1_200): String {
    var t = markdown.replace(Regex("```[\\s\\S]*?(```|$)"), " ")
    t = t.replace(Regex("`([^`]*)`"), "$1")
    t = t.replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")
    t = t.replace(Regex("(?m)^\\s{0,3}(#{1,6}|[-*+]|\\d+[.)]|>)\\s+"), "")
    t = t.replace(Regex("(\\*\\*|__|\\*|_|~~)(\\S[^\\n]*?)\\1"), "$2")
    t = t.replace(Regex("(?m)^\\s*\\|.*\\|\\s*$"), " ")
    t = t.replace(Regex("https?://\\S+"), "")
    t = t.replace(Regex("[ \\t]+"), " ").replace(Regex("(?m)^ | $"), "").replace(Regex("\\n{2,}"), "\n").trim()
    if (t.length <= max) return t
    val cut = t.take(max)
    val end = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf("\n"))
    return (if (end > max / 2) cut.take(end + 1) else cut).trim()
}

/**
 * A hands-free voice call with Orbit: listen, send what was said to the open chat, wait for the reply,
 * read it aloud, listen again, until [hangUp]. It only uses the chat's own send and reply, so everything
 * said and answered stays in the conversation, and anything that needs a tap still happens on screen.
 */
class OrbitCall(
    private val scope: CoroutineScope,
    private val voice: VoiceSource,
    private val speaker: CallSpeaker,
    private val exchanges: Flow<List<Exchange>>,
    private val send: (String) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val needsScreenLine: String = "",
) {
    private val _phase = MutableStateFlow<CallPhase>(CallPhase.Connecting)
    val phase: StateFlow<CallPhase> = _phase.asStateFlow()
    private var job: Job? = null
    private var session: SpeechSession? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            try {
                while (true) {
                    val said = listen() ?: break
                    if (said.isBlank()) continue
                    val before = exchanges.first().size
                    _phase.value = CallPhase.Thinking(said)
                    if (!send(said)) {
                        speakLine(needsScreenLine)
                        continue
                    }
                    var reply = awaitReply(before, skip = null)
                    // A step that needs the screen is said once; the call then waits for the run to go on.
                    while (reply is Reply.Screen) {
                        _phase.value = CallPhase.NeedsScreen(reply.what)
                        speaker.speak(spokenText(reply.what))
                        reply = awaitReply(before, skip = reply)
                    }
                    speakLine((reply as Reply.Answer).text)
                }
            } finally {
                session?.cancel()
                speaker.stop()
                if (_phase.value !is CallPhase.Failed) _phase.value = CallPhase.Ended
            }
        }
    }

    fun hangUp() {
        session?.cancel()
        speaker.stop()
        job?.cancel()
        job = null
        speaker.close()
        if (_phase.value !is CallPhase.Failed) _phase.value = CallPhase.Ended
    }

    /** One turn of listening; null when the call can't go on (no permission, mic busy). */
    private suspend fun listen(): String? {
        _phase.value = CallPhase.Listening()
        val s = voice.start()
        session = s
        val end = EndOfSpeech()
        var heard = ""
        var released = false
        var result: String? = ""
        s.events.collect { e ->
            when (e) {
                is SpeechEvent.Level -> {
                    _phase.value = CallPhase.Listening(e.value, heard)
                    if (!released && end.feed(e.value, now())) { released = true; s.release() }
                }
                is SpeechEvent.Partial -> {
                    heard = e.stable + e.tail
                    _phase.value = CallPhase.Listening((_phase.value as? CallPhase.Listening)?.level ?: 0f, heard)
                }
                SpeechEvent.Converting -> Unit
                is SpeechEvent.Final -> result = e.text
                is SpeechEvent.Failed -> {
                    result = when (e.why) {
                        // A missed sentence or a network blip ends this turn only; the call goes on.
                        SpeechFailure.Network, SpeechFailure.Busy -> ""
                        else -> { _phase.value = CallPhase.Failed(e.why); null }
                    }
                }
            }
        }
        session = null
        return result
    }

    private sealed interface Reply {
        data class Answer(val text: String) : Reply
        data class Screen(val what: String) : Reply
    }

    private suspend fun awaitReply(before: Int, skip: Reply?): Reply {
        var reply: Reply? = null
        exchanges.first { list ->
            val ex = list.drop(before).lastOrNull() ?: return@first false
            reply = when (val st = ex.status) {
                is ExchangeStatus.Done -> Reply.Answer(st.answer)
                is ExchangeStatus.Failed -> Reply.Answer(st.reason)
                is ExchangeStatus.Cancelled -> if (st.summarizing) null else Reply.Answer(st.summary.orEmpty())
                is ExchangeStatus.NeedsInput -> Reply.Answer(st.prompt)
                is ExchangeStatus.NeedsConfirmation -> Reply.Screen(st.title)
                is ExchangeStatus.NeedsTakeover -> Reply.Screen(st.message)
                is ExchangeStatus.Paused -> if (st.resumable) Reply.Screen(st.why) else null
                else -> null
            }
            reply != null && reply != skip
        }
        return reply!!
    }

    private suspend fun speakLine(text: String) {
        val spoken = spokenText(text)
        if (spoken.isBlank()) return
        _phase.value = CallPhase.Speaking(spoken)
        speaker.speak(spoken)
    }
}

/** Android's built-in text to speech, in the app's language; speaking is skipped when no voice is installed. */
class SystemCallSpeaker(context: Context, private val locale: Locale = Locale.getDefault()) : CallSpeaker {
    private val ready = CompletableDeferred<Boolean>()
    private val tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }

    override suspend fun speak(text: String) {
        if (!ready.await()) return
        tts.language = locale
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine { cont ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(Unit) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(Unit) }
                override fun onStop(utteranceId: String?, interrupted: Boolean) { if (utteranceId == id && cont.isActive) cont.resume(Unit) }
            })
            cont.invokeOnCancellation { tts.stop() }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS && cont.isActive) cont.resume(Unit)
        }
    }

    override fun stop() { tts.stop() }

    override fun close() { tts.shutdown() }
}
