package com.wangzan101.mika.core.cap.phone

/**
 * Finds shell commands that remove an app or wipe its data: `pm uninstall`, `cmd package uninstall`,
 * `pm uninstall-system-updates`, `pm clear` and `cmd package clear`, wherever they sit in the line
 * (after `;`, `&&`, a pipe, inside `sh -c "…"`). Such a command asks first in Ask mode, like a delete on screen.
 *
 * This reads the command as text. A command that hides the words (variables, `eval`, an encoded script) is not
 * recognised here.
 */
internal object PackageRemoval {
    private val REMOVAL = Regex("(?<![\\w./-])(?:pm|cmd\\s+package)\\s+(uninstall-system-updates|uninstall|clear)(?![\\w-])([^;&|\\n]*)")
    /** Options of `pm uninstall` and `pm clear` that take a value. */
    private val VALUE_FLAGS = setOf("--user", "--versionCode", "--cache-only-user")
    private val PACKAGE = Regex("[A-Za-z][\\w]*(?:\\.[\\w]+)+")

    /**
     * The package a command removes or wipes; "" when it removes one that can't be read out of the text;
     * null when the command removes nothing.
     */
    fun target(command: String): String? {
        val match = REMOVAL.find(command) ?: return null
        val words = match.groupValues[2].trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var skip = false
        for (word in words) {
            if (skip) { skip = false; continue }
            if (word in VALUE_FLAGS) { skip = true; continue }
            if (word.startsWith("-")) continue
            val name = word.trim('"', '\'', '`', ')', '(')
            return if (PACKAGE.matches(name)) name else ""
        }
        return ""
    }
}
