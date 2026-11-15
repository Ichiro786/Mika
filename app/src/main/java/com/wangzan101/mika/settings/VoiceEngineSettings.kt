package com.wangzan101.mika.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which engine the header call button talks through. */
enum class VoiceEngine(val wire: String) {
    /** On-device listening and text to speech over the open chat (the original call). */
    Builtin("builtin"),
    /** Google's Gemini Live API, with the user's own Gemini API key. */
    GeminiLive("gemini_live"),
    /** OpenAI's Realtime API, with the user's own OpenAI API key. */
    OpenAiRealtime("openai_realtime"),
    ;

    companion object {
        fun of(wire: String?): VoiceEngine = entries.firstOrNull { it.wire == wire } ?: Builtin
    }
}

/** A key for a live engine and where it came from ([fromProvider] is the model provider's name when reused). */
data class VoiceKey(val key: String, val fromProvider: String? = null)

/**
 * The live call engine, its model and voice, and its own API keys (encrypted with a key in the Android
 * keystore). When no key was typed here, a key already saved for the same company's chat model is reused.
 */
class VoiceEngineSettings(
    private val prefs: SharedPreferences,
    private val cipher: KeyCipher = KeystoreKeyCipher("mika-voice-engine-keys"),
    /** The model providers, to reuse a Gemini or OpenAI key the user already saved. */
    private val providers: () -> ProviderStore? = { null },
) {
    private val _engine = MutableStateFlow(VoiceEngine.of(prefs.getString(ENGINE, null)))
    val engine: StateFlow<VoiceEngine> = _engine.asStateFlow()

    fun setEngine(e: VoiceEngine) {
        prefs.edit().putString(ENGINE, e.wire).apply()
        _engine.value = e
    }

    /** The key typed on the Voice page, or empty. */
    fun ownKey(e: VoiceEngine): String = prefs.getString(keyPref(e), null)?.let {
        runCatching { String(cipher.decrypt(Base64.decode(it, Base64.NO_WRAP))) }.getOrNull()
    }.orEmpty()

    fun setKey(e: VoiceEngine, key: String) {
        val k = key.trim()
        prefs.edit().apply {
            if (k.isEmpty()) remove(keyPref(e))
            else putString(keyPref(e), Base64.encodeToString(cipher.encrypt(k.toByteArray()), Base64.NO_WRAP))
        }.apply()
    }

    /** The key a call would use: the one typed here, else a saved model provider's key for the same company. */
    fun key(e: VoiceEngine): VoiceKey? {
        ownKey(e).takeIf { it.isNotBlank() }?.let { return VoiceKey(it) }
        val store = providers() ?: return null
        val p = store.providers.value.firstOrNull { p ->
            when (e) {
                VoiceEngine.GeminiLive -> p.kind == ProviderConfig.Kind.Gemini && "generativelanguage.googleapis.com" in p.baseUrl
                VoiceEngine.OpenAiRealtime -> p.kind != ProviderConfig.Kind.ChatGpt && "api.openai.com" in p.baseUrl
                VoiceEngine.Builtin -> false
            }
        } ?: return null
        return store.providerKey(p.id)?.takeIf { it.isNotBlank() }?.let { VoiceKey(it, p.name) }
    }

    fun model(e: VoiceEngine): String = prefs.getString("model_${e.wire}", null)?.takeIf { it.isNotBlank() } ?: defaultModel(e)

    fun setModel(e: VoiceEngine, model: String) {
        prefs.edit().putString("model_${e.wire}", model.trim()).apply()
    }

    /** The chosen voice name; empty means the service's default voice. */
    fun voice(e: VoiceEngine): String = prefs.getString("voice_${e.wire}", null).orEmpty()

    fun setVoice(e: VoiceEngine, voice: String) {
        prefs.edit().putString("voice_${e.wire}", voice).apply()
    }

    companion object {
        private const val PREFS = "voice_engine"
        private const val ENGINE = "engine"
        private fun keyPref(e: VoiceEngine) = "api_key_encrypted_${e.wire}"

        const val DEFAULT_GEMINI_MODEL = "gemini-3.8-live"
        const val DEFAULT_OPENAI_MODEL = "gpt-realtime-2.1"
        val GEMINI_VOICES = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr")
        val OPENAI_VOICES = listOf("marin", "cedar", "alloy", "ash", "ballad", "coral", "echo", "sage", "shimmer", "verse")

        fun defaultModel(e: VoiceEngine): String = when (e) {
            VoiceEngine.GeminiLive -> DEFAULT_GEMINI_MODEL
            VoiceEngine.OpenAiRealtime -> DEFAULT_OPENAI_MODEL
            VoiceEngine.Builtin -> ""
        }

        fun voices(e: VoiceEngine): List<String> = when (e) {
            VoiceEngine.GeminiLive -> GEMINI_VOICES
            VoiceEngine.OpenAiRealtime -> OPENAI_VOICES
            VoiceEngine.Builtin -> emptyList()
        }

        @Volatile private var instance: VoiceEngineSettings? = null

        fun get(context: Context): VoiceEngineSettings = instance ?: synchronized(this) {
            instance ?: context.applicationContext.let { app ->
                VoiceEngineSettings(
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                    providers = { (app as? ProviderStore.Owner)?.providers },
                ).also { instance = it }
            }
        }
    }
}
