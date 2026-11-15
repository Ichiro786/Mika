package com.wangzan101.mika.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import com.wangzan101.mika.core.net.NetProxy
import com.wangzan101.mika.settings.VoiceEngine
import com.wangzan101.mika.ui.Exchange
import com.wangzan101.mika.ui.ExchangeStatus
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/** What the chat said back to one request, as a call can use it. */
sealed interface CallReply {
    data class Answer(val text: String) : CallReply
    data class Screen(val what: String) : CallReply
}

/** Waits for the newest exchange after [before] to settle into an answer or a step that needs the screen. */
suspend fun awaitCallReply(exchanges: Flow<List<Exchange>>, before: Int, skip: CallReply? = null): CallReply {
    var reply: CallReply? = null
    exchanges.first { list ->
        val ex = list.drop(before).lastOrNull() ?: return@first false
        reply = when (val st = ex.status) {
            is ExchangeStatus.Done -> CallReply.Answer(st.answer)
            is ExchangeStatus.Failed -> CallReply.Answer(st.reason)
            is ExchangeStatus.Cancelled -> if (st.summarizing) null else CallReply.Answer(st.summary.orEmpty())
            is ExchangeStatus.NeedsInput -> CallReply.Answer(st.prompt)
            is ExchangeStatus.NeedsConfirmation -> CallReply.Screen(st.title)
            is ExchangeStatus.NeedsTakeover -> CallReply.Screen(st.message)
            is ExchangeStatus.Paused -> if (st.resumable) CallReply.Screen(st.why) else null
            else -> null
        }
        reply != null && reply != skip
    }
    return reply!!
}

/**
 * A live voice call through Gemini Live or OpenAI Realtime with the user's own API key. The voice model
 * talks with the caller directly and hands anything that needs doing on the phone to Orbit through one
 * tool, run_orbit_task, which sends the request to the open chat and returns its answer.
 */
class RealtimeCall(
    private val scope: CoroutineScope,
    private val engine: VoiceEngine,
    private val key: String,
    private val model: String,
    private val voiceName: String,
    private val instructions: String,
    private val exchanges: Flow<List<Exchange>>,
    private val send: (String) -> Boolean,
    private val cantStartLine: String,
) {
    private val _phase = MutableStateFlow<CallPhase>(CallPhase.Connecting)
    val phase: StateFlow<CallPhase> = _phase.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val gemini = engine == VoiceEngine.GeminiLive
    private val inRate = if (gemini) 16_000 else 24_000
    private val outRate = 24_000
    private val json = Json { ignoreUnknownKeys = true }

    private var ws: WebSocket? = null
    private val jobs = java.util.concurrent.CopyOnWriteArrayList<Job>()
    @Volatile private var ready = false
    @Volatile private var closed = false
    @Volatile private var playEnd = 0L
    @Volatile private var heard = ""
    @Volatile private var saying = ""
    private val tools = java.util.concurrent.atomic.AtomicInteger(0)
    private val toolsRunning get() = tools.get()
    private val playback = Channel<ByteArray>(Channel.UNLIMITED)
    private var track: AudioTrack? = null

    fun start() {
        if (ws != null) return
        val client = NetProxy.shared.client(
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).pingInterval(15, TimeUnit.SECONDS),
        )
        val request = if (gemini) {
            Request.Builder()
                .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key")
                .build()
        } else {
            Request.Builder().url("wss://api.openai.com/v1/realtime?model=$model").header("Authorization", "Bearer $key").build()
        }
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(setupMessage())
                if (!gemini) onReady()
            }
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                fail(response?.let { "${it.code} ${it.message}".trim() } ?: t.message ?: t.javaClass.simpleName)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed && code != 1000) fail(reason.ifBlank { "closed ($code)" }) else if (!closed) hangUp()
            }
        })
        jobs += scope.launch(Dispatchers.IO) { playLoop() }
    }

    fun hangUp() {
        if (closed) return
        closed = true
        ws?.close(1000, null)
        jobs.forEach { it.cancel() }
        playback.close()
        runCatching { track?.stop(); track?.release() }
        track = null
        if (_phase.value !is CallPhase.Failed) _phase.value = CallPhase.Ended
    }

    private fun fail(why: String) {
        if (closed) return
        _error.value = why.take(160)
        _phase.value = CallPhase.Failed(SpeechFailure.Network)
        hangUp()
    }

    private fun onReady() {
        if (ready) return
        ready = true
        _phase.value = CallPhase.Listening()
        jobs += scope.launch(Dispatchers.IO) { micLoop() }
    }

    // ---- Audio ----

    @SuppressLint("MissingPermission")
    private suspend fun micLoop() {
        val frame = inRate / 50 // 20 ms
        val min = AudioRecord.getMinBufferSize(inRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, inRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frame * 8))
        }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            _phase.value = CallPhase.Failed(SpeechFailure.MicUnavailable); hangUp(); return
        }
        val aec = if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
        val ns = if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
        val buf = ShortArray(frame)
        val bytes = ByteArray(frame * 2)
        val silent = ByteArray(frame * 2)
        try {
            rec.startRecording()
            while (scope.isActive && !closed) {
                val n = rec.read(buf, 0, frame)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) {
                    val s = buf[i].toInt()
                    sum += s * s
                    bytes[i * 2] = (s and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                }
                val level = (sqrt(sum / n) / 32768.0 * 5).toFloat().coerceIn(0f, 1f)
                val playing = System.currentTimeMillis() < playEnd
                // While Orbit speaks, only a clearly louder voice goes up, so its own sound isn't heard as the caller.
                val out = if (playing && level < 0.35f) silent else bytes
                sendAudio(out, n * 2)
                updatePhase(level, playing)
            }
        } finally {
            runCatching { rec.stop() }
            rec.release(); aec?.release(); ns?.release()
        }
    }

    private fun updatePhase(level: Float, playing: Boolean) {
        val p = _phase.value
        if (p is CallPhase.Failed || p is CallPhase.Ended || p is CallPhase.NeedsScreen && toolsRunning > 0) return
        _phase.value = when {
            playing -> CallPhase.Speaking(saying)
            toolsRunning > 0 -> p as? CallPhase.Thinking ?: CallPhase.Thinking(heard)
            else -> CallPhase.Listening(level, heard)
        }
    }

    private suspend fun playLoop() {
        val min = AudioTrack.getMinBufferSize(outRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(outRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
            )
            .setBufferSizeInBytes(maxOf(min, outRate / 5))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        for (chunk in playback) {
            val now = System.currentTimeMillis()
            playEnd = maxOf(playEnd, now) + chunk.size * 1000L / (outRate * 2)
            t.write(chunk, 0, chunk.size)
        }
    }

    /** The caller spoke over Orbit: drop what's still queued and stop at once. */
    private fun interrupt() {
        while (playback.tryReceive().isSuccess) Unit
        runCatching { track?.pause(); track?.flush(); track?.play() }
        playEnd = 0
    }

    private fun sendAudio(pcm: ByteArray, len: Int) {
        val b64 = Base64.encodeToString(pcm, 0, len, Base64.NO_WRAP)
        val msg = if (gemini) {
            """{"realtimeInput":{"audio":{"data":"$b64","mimeType":"audio/pcm;rate=$inRate"}}}"""
        } else {
            """{"type":"input_audio_buffer.append","audio":"$b64"}"""
        }
        ws?.send(msg)
    }

    // ---- Protocol ----

    private val toolDescription =
        "Hand a request to Orbit, the phone agent, which can operate this Android phone and its apps " +
            "(messages, calls, settings, browsing, files, reminders and more). Use it for anything that needs doing " +
            "or looking up on the phone. Returns Orbit's answer, or says the phone screen needs the user."

    private val requestDescription = "What to do, in the caller's words, with every detail they gave."

    private fun setupMessage(): String = if (gemini) {
        buildJsonObject {
            putJsonObject("setup") {
                put("model", if (model.startsWith("models/")) model else "models/$model")
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") { add("AUDIO") }
                    if (voiceName.isNotBlank()) {
                        putJsonObject("speechConfig") {
                            putJsonObject("voiceConfig") { putJsonObject("prebuiltVoiceConfig") { put("voiceName", voiceName) } }
                        }
                    }
                }
                putJsonObject("systemInstruction") { putJsonArray("parts") { add(buildJsonObject { put("text", instructions) }) } }
                putJsonArray("tools") {
                    add(buildJsonObject {
                        putJsonArray("functionDeclarations") {
                            add(buildJsonObject {
                                put("name", TOOL)
                                put("description", toolDescription)
                                putJsonObject("parameters") {
                                    put("type", "OBJECT")
                                    putJsonObject("properties") {
                                        putJsonObject("request") { put("type", "STRING"); put("description", requestDescription) }
                                    }
                                    putJsonArray("required") { add("request") }
                                }
                            })
                        }
                    })
                }
                putJsonObject("inputAudioTranscription") {}
                putJsonObject("outputAudioTranscription") {}
            }
        }.toString()
    } else {
        buildJsonObject {
            put("type", "session.update")
            putJsonObject("session") {
                put("type", "realtime")
                put("instructions", instructions)
                putJsonArray("output_modalities") { add("audio") }
                putJsonObject("audio") {
                    putJsonObject("input") {
                        putJsonObject("format") { put("type", "audio/pcm"); put("rate", inRate) }
                        putJsonObject("turn_detection") { put("type", "semantic_vad") }
                        putJsonObject("transcription") { put("model", "gpt-4o-mini-transcribe") }
                    }
                    putJsonObject("output") {
                        putJsonObject("format") { put("type", "audio/pcm"); put("rate", outRate) }
                        if (voiceName.isNotBlank()) put("voice", voiceName)
                    }
                }
                putJsonArray("tools") {
                    add(buildJsonObject {
                        put("type", "function")
                        put("name", TOOL)
                        put("description", toolDescription)
                        putJsonObject("parameters") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("request") { put("type", "string"); put("description", requestDescription) }
                            }
                            putJsonArray("required") { add("request") }
                        }
                    })
                }
                put("tool_choice", "auto")
            }
        }.toString()
    }

    private fun handle(text: String) {
        val msg = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        if (gemini) handleGemini(msg) else handleOpenAi(msg)
    }

    private fun handleGemini(msg: JsonObject) {
        if ("setupComplete" in msg) onReady()
        msg["error"]?.let { fail(it.jsonObject.str("message") ?: it.toString()); return }
        msg["serverContent"]?.jsonObject?.let { sc ->
            if (sc["interrupted"]?.jsonPrimitive?.contentOrNull == "true") { interrupt(); saying = "" }
            sc["inputTranscription"]?.jsonObject?.str("text")?.let { heard = (heard + it).takeLast(400); saying = "" }
            sc["outputTranscription"]?.jsonObject?.str("text")?.let { saying = (saying + it).takeLast(400) }
            sc["modelTurn"]?.jsonObject?.get("parts")?.jsonArray?.forEach { part ->
                part.jsonObject["inlineData"]?.jsonObject?.str("data")?.let { playback.trySend(Base64.decode(it, Base64.DEFAULT)) }
            }
            if (sc["turnComplete"]?.jsonPrimitive?.contentOrNull == "true") heard = ""
        }
        msg["toolCall"]?.jsonObject?.get("functionCalls")?.jsonArray?.forEach { c ->
            val call = c.jsonObject
            val id = call.str("id").orEmpty()
            val name = call.str("name").orEmpty()
            val req = call["args"]?.jsonObject?.str("request").orEmpty()
            runTool(req) { result ->
                buildJsonObject {
                    putJsonObject("toolResponse") {
                        putJsonArray("functionResponses") {
                            add(buildJsonObject {
                                put("id", id); put("name", name)
                                putJsonObject("response") { put("result", result) }
                            })
                        }
                    }
                }.toString().let { listOf(it) }
            }
        }
        if ("goAway" in msg) fail("the session ended")
    }

    private fun handleOpenAi(msg: JsonObject) {
        when (msg.str("type")) {
            "response.output_audio.delta", "response.audio.delta" ->
                msg.str("delta")?.let { playback.trySend(Base64.decode(it, Base64.DEFAULT)) }
            "response.output_audio_transcript.delta", "response.audio_transcript.delta" ->
                msg.str("delta")?.let { saying = (saying + it).takeLast(400) }
            "conversation.item.input_audio_transcription.completed" -> msg.str("transcript")?.let { heard = it.takeLast(400) }
            "input_audio_buffer.speech_started" -> {
                interrupt(); saying = ""; heard = ""
                ws?.send("""{"type":"response.cancel"}""")
            }
            "response.function_call_arguments.done" -> {
                val callId = msg.str("call_id").orEmpty()
                val req = runCatching { json.parseToJsonElement(msg.str("arguments").orEmpty()).jsonObject.str("request") }.getOrNull().orEmpty()
                runTool(req) { result ->
                    listOf(
                        buildJsonObject {
                            put("type", "conversation.item.create")
                            putJsonObject("item") { put("type", "function_call_output"); put("call_id", callId); put("output", result) }
                        }.toString(),
                        """{"type":"response.create"}""",
                    )
                }
            }
            "error" -> {
                val e = msg["error"]?.jsonObject
                // Cancelling when nothing is playing is harmless; anything else ends the call with its reason.
                if (e?.str("code") != "response_cancel_not_active") fail(e?.str("message") ?: "error")
            }
        }
    }

    /** Runs Orbit on [request] off the socket thread and sends [reply]'s messages back with its answer. */
    private fun runTool(request: String, reply: (String) -> List<String>) {
        tools.incrementAndGet()
        _phase.value = CallPhase.Thinking(request)
        jobs += scope.launch {
            try {
                val before = exchanges.first().size
                val started = withContext(Dispatchers.Main) { send(request) }
                if (!started) { reply(cantStartLine).forEach { ws?.send(it) }; return@launch }
                val first = awaitCallReply(exchanges, before)
                val result = when (first) {
                    is CallReply.Answer -> first.text
                    is CallReply.Screen -> {
                        _phase.value = CallPhase.NeedsScreen(first.what)
                        "Orbit needs the user on the phone screen: ${first.what}. Tell them to look at their phone. " +
                            "You'll get Orbit's answer as a message once it goes on."
                    }
                }
                reply(spokenText(result, 4_000)).forEach { ws?.send(it) }
                if (first is CallReply.Screen) {
                    var next: CallReply = first
                    while (next is CallReply.Screen) next = awaitCallReply(exchanges, before, skip = next)
                    tell("Orbit finished the earlier task. Its answer: ${spokenText((next as CallReply.Answer).text, 4_000)}")
                }
            } finally {
                tools.decrementAndGet()
            }
        }
    }

    /** Puts a note into the conversation and asks the voice model to say it. */
    private fun tell(text: String) {
        if (gemini) {
            ws?.send(
                buildJsonObject {
                    putJsonObject("clientContent") {
                        putJsonArray("turns") {
                            add(buildJsonObject {
                                put("role", "user")
                                putJsonArray("parts") { add(buildJsonObject { put("text", text) }) }
                            })
                        }
                        put("turnComplete", true)
                    }
                }.toString(),
            )
        } else {
            ws?.send(
                buildJsonObject {
                    put("type", "conversation.item.create")
                    putJsonObject("item") {
                        put("type", "message"); put("role", "user")
                        putJsonArray("content") { add(buildJsonObject { put("type", "input_text"); put("text", text) }) }
                    }
                }.toString(),
            )
            ws?.send("""{"type":"response.create"}""")
        }
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull

    companion object {
        const val TOOL = "run_orbit_task"

        /** How the voice model should behave in a call; the user's own instructions follow, if any. */
        fun instructions(userInstructions: String): String {
            val base = "You are Orbit, a friendly assistant on the user's Android phone, in a live voice call with them. " +
                "Keep replies short and natural, one or two sentences, in the language the caller uses. " +
                "For anything that needs doing or checking on the phone or in its apps, call $TOOL with the full request, " +
                "say briefly that you're on it, then tell them the result in a sentence. " +
                "If the result says the screen is needed, tell them to look at their phone. Never invent results."
            val mine = userInstructions.trim()
            return if (mine.isEmpty()) base else "$base\n\nThe user's own instructions:\n$mine"
        }
    }
}
