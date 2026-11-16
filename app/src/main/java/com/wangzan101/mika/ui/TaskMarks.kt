package com.wangzan101.mika.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the user did to tasks on the Tasks page: marked one done, or removed one from the list. It is a note
 * about the list kept on this phone, not task state: running, stopping and results stay with the runtime, and a
 * removed task's chat is untouched.
 */
class TaskMarks private constructor(context: Context) {
    data class State(val done: Set<String> = emptySet(), val deleted: Set<String> = emptySet())

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(State(read(DONE).toSet(), read(DELETED).toSet()))
    val state: StateFlow<State> = _state.asStateFlow()

    fun markDone(ids: Collection<String>) = add(DONE, ids) { s, all -> s.copy(done = all) }
    fun delete(ids: Collection<String>) = add(DELETED, ids) { s, all -> s.copy(deleted = all) }

    private fun add(key: String, ids: Collection<String>, apply: (State, Set<String>) -> State) {
        if (ids.isEmpty()) return
        val kept = kept(read(key), ids)
        prefs.edit().putString(key, kept.joinToString("\n")).apply()
        _state.update { apply(it, kept.toSet()) }
    }

    private fun read(key: String): List<String> = prefs.getString(key, "").orEmpty().split('\n').filter { it.isNotEmpty() }

    companion object {
        private const val PREFS = "task_marks"
        private const val DONE = "done"
        private const val DELETED = "deleted"
        /** Marks kept per kind; the run history itself keeps far fewer runs, so older marks have nothing left to match. */
        internal const val MAX = 600

        /** [existing] with [added] at the end, without repeats, trimmed to the newest [MAX]. */
        internal fun kept(existing: List<String>, added: Collection<String>): List<String> =
            (existing.filterNot { it in added } + added.distinct()).takeLast(MAX)

        @Volatile private var instance: TaskMarks? = null
        fun shared(context: Context): TaskMarks = instance ?: synchronized(this) {
            instance ?: TaskMarks(context).also { instance = it }
        }
    }
}
