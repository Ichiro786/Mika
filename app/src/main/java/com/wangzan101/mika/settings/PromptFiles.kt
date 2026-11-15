package com.wangzan101.mika.settings

import android.content.Context
import com.wangzan101.mika.core.agent.SystemPrompt
import java.io.File

/** The two prompt files the user can read and edit in Personalization. */
enum class PromptFile(val fileName: String) {
    /** The user's own rules, added after the system prompt on every task. Empty by default. */
    Instructions("instructions.md"),
    /** Orbit's identity and principles. Defaults to the text that ships with the app. */
    SystemPrompt("system_prompt.md"),
}

/**
 * A thin layer over the agent's own prompt: [identity] takes whatever the prompt builder made and swaps in
 * an edited system prompt and appends the user's instructions. Nothing is stored until the user edits a file,
 * and Reset deletes the file, so the shipped prompt (and any later update to it) is used again.
 */
class PromptFiles(private val dir: File, private val shipped: () -> String = { SystemPrompt.identity() }) {
    private fun file(f: PromptFile) = File(dir, f.fileName)

    fun default(f: PromptFile): String = if (f == PromptFile.SystemPrompt) shipped() else ""

    fun read(f: PromptFile): String = file(f).takeIf { it.isFile }?.readText() ?: default(f)

    /** True when the user has changed the file from its default. */
    fun edited(f: PromptFile): Boolean = file(f).isFile

    /** Saves [text]; the same as the default (or empty instructions) removes the file. */
    fun write(f: PromptFile, text: String) {
        val same = text.isBlank() && f == PromptFile.Instructions || text.trim() == default(f).trim()
        if (same) { reset(f); return }
        dir.mkdirs()
        val tmp = File(dir, "${f.fileName}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file(f))) { file(f).writeText(text); tmp.delete() }
    }

    fun reset(f: PromptFile) {
        file(f).delete()
    }

    /** The prompt a task starts with: [built] (the agent's own), or the edited system prompt, plus the user's instructions. */
    fun identity(built: String): String {
        val base = file(PromptFile.SystemPrompt).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() } ?: built
        val mine = file(PromptFile.Instructions).takeIf { it.isFile }?.readText()?.trim().orEmpty()
        return if (mine.isEmpty()) base else "$base\n\n# The user's own instructions\nFollow these unless they conflict with safety or the user's request in this task.\n\n$mine"
    }

    companion object {
        fun get(context: Context) = PromptFiles(File(context.applicationContext.filesDir, "prompts"))
    }
}
