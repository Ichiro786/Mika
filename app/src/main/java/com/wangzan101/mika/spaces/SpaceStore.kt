package com.wangzan101.mika.spaces

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A space: a small live panel Orbit builds from one sentence (a tracker, a dashboard, controls),
 * after Meta's Muse spaces. Orbit builds and refines it in its own conversation, writing one
 * self-contained page ([SpacePanel.FILE]) into that conversation's workspace, which the space shows.
 */
@Serializable
data class Space(
    val id: String,
    val name: String,
    val emoji: String,
    /** The user's own sentence, kept so a rebuild or refresh knows what the space is for. */
    val description: String,
    /** The conversation where Orbit builds, refines and refreshes this space. */
    val conversationId: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** The schedule that refreshes it in the background, when one is set. */
    val refreshPlanId: String? = null,
    val pinnedAt: Long? = null,
    val hidden: Boolean = false,
)

@Serializable
private data class SpaceFile(val version: Int = 1, val spaces: List<Space> = emptyList())

/**
 * Spaces on disk as one small JSON file, written atomically. Order: pinned first (latest pin on top),
 * then newest first. Every change is published on [spaces] so the sidebar redraws in place.
 */
class SpaceStore(private val file: File, private val now: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private val state = MutableStateFlow(read())
    val spaces: StateFlow<List<Space>> = state.asStateFlow()

    fun get(id: String): Space? = state.value.firstOrNull { it.id == id }

    fun forConversation(conversationId: String): Space? = state.value.firstOrNull { it.conversationId == conversationId }

    /** A new space from the user's sentence; the name and emoji fall back to ones drawn from it. */
    fun create(description: String, name: String? = null, emoji: String? = null, conversationId: String = UUID.randomUUID().toString()): Space {
        val clean = description.trim()
        require(clean.isNotEmpty()) { "A space needs a description" }
        val at = now()
        val space = Space(
            id = UUID.randomUUID().toString(),
            name = name?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME) ?: SpaceNames.nameFor(clean),
            emoji = emoji?.trim()?.takeIf { it.isNotEmpty() } ?: SpaceNames.emojiFor(clean),
            description = clean.take(MAX_DESCRIPTION),
            conversationId = conversationId,
            createdAt = at,
        )
        update { it + space }
        return space
    }

    fun rename(id: String, name: String): Boolean {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return false
        return change(id) { it.copy(name = clean, updatedAt = now()) }
    }

    fun setEmoji(id: String, emoji: String): Boolean {
        val clean = emoji.trim()
        if (clean.isEmpty()) return false
        return change(id) { it.copy(emoji = clean, updatedAt = now()) }
    }

    fun setPinned(id: String, pinned: Boolean): Boolean = change(id) { it.copy(pinnedAt = if (pinned) now() else null) }

    fun setHidden(id: String, hidden: Boolean): Boolean = change(id) { it.copy(hidden = hidden) }

    fun setRefreshPlan(id: String, planId: String?): Boolean = change(id) { it.copy(refreshPlanId = planId) }

    /** Marks the panel as rebuilt or refreshed, so the sidebar can order and label it. */
    fun touch(id: String): Boolean = change(id) { it.copy(updatedAt = now()) }

    fun delete(id: String): Space? {
        var removed: Space? = null
        update { list -> list.filterNot { (it.id == id).also { hit -> if (hit) removed = it } } }
        return removed
    }

    private fun change(id: String, edit: (Space) -> Space): Boolean {
        var hit = false
        update { list -> list.map { if (it.id == id) edit(it).also { hit = true } else it } }
        return hit
    }

    private fun update(edit: (List<Space>) -> List<Space>) = synchronized(lock) {
        val next = ordered(edit(state.value))
        if (next == state.value) return@synchronized
        write(next)
        state.value = next
    }

    private fun read(): List<Space> = runCatching {
        if (!file.exists()) emptyList() else ordered(JSON.decodeFromString<SpaceFile>(file.readText()).spaces)
    }.getOrDefault(emptyList())

    private fun write(list: List<Space>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(JSON.encodeToString(SpaceFile(spaces = list)).toByteArray())
            out.fd.sync()
        }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        const val MAX_NAME = 40
        const val MAX_DESCRIPTION = 600
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        internal fun ordered(list: List<Space>): List<Space> =
            list.sortedWith(compareByDescending<Space> { it.pinnedAt ?: Long.MIN_VALUE }.thenByDescending { it.createdAt })

        @Volatile private var shared: SpaceStore? = null

        fun shared(context: Context): SpaceStore = shared ?: synchronized(this) {
            shared ?: SpaceStore(File(context.applicationContext.filesDir, "spaces/spaces.json")).also { shared = it }
        }
    }
}

/** A short name and a fitting emoji drawn from the user's sentence, until they pick their own. */
internal object SpaceNames {
    private val lead = Regex("^(?i)(make|build|create|give|add|i want|i need|show)( me)?( a| an| the)? ", RegexOption.IGNORE_CASE)

    private val stops = setOf("for", "to", "with", "that", "which", "from", "about", "so", "where", "when")

    fun nameFor(description: String): String {
        val first = description.lineSequence().first().trim().trimEnd('.', '!', '?')
        val stripped = first.replace(lead, "").trim()
        // Up to four words, stopping before a connecting word, so "VM dashboard for my servers" is "VM dashboard".
        val words = stripped.split(Regex("\\s+")).filter { it.isNotEmpty() }
            .takeWhile { it.lowercase() !in stops }.take(4).joinToString(" ")
        val name = words.ifEmpty { first }.take(SpaceStore.MAX_NAME)
        return name.replaceFirstChar { it.uppercase() }
    }

    private val picks = listOf(
        listOf("server", "vm", "cloud", "deploy", "host", "docker") to "🖥️",
        listOf("habit", "streak", "routine") to "✅",
        listOf("workout", "gym", "run", "fitness", "steps") to "🏋️",
        listOf("food", "meal", "recipe", "diet", "grocer") to "🥗",
        listOf("money", "budget", "expense", "spend", "finance", "stock") to "💰",
        listOf("travel", "trip", "flight", "hotel") to "✈️",
        listOf("study", "learn", "course", "exam", "read", "book") to "📚",
        listOf("plan", "calendar", "schedule", "week") to "📅",
        listOf("project", "task", "todo", "board") to "📋",
        listOf("weather") to "⛅",
        listOf("game", "play") to "🎮",
        listOf("music", "song", "playlist") to "🎵",
        listOf("news", "feed") to "📰",
    )

    fun emojiFor(description: String): String {
        val text = description.lowercase()
        return picks.firstOrNull { (words, _) -> words.any { Regex("\\b$it").containsMatchIn(text) } }?.second ?: "✨"
    }
}
