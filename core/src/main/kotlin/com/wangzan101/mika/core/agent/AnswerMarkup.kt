package com.wangzan101.mika.core.agent

/**
 * Lines the model may put at the very start of its final answer to act like a chat partner:
 * `[[react:👍]]` reacts to the user's latest message with one of [REACTIONS], and
 * `[[reply:exact words]]` quotes the earlier message of the user that the answer is about.
 * The app shows them as a reaction under the user's bubble and a quote above the answer,
 * and the lines never reach the visible text, the stored outcome or a notification.
 */
object AnswerMarkup {
    /** The reactions both the user and Orbit can use, in the order the picker shows them. */
    val REACTIONS = listOf("❤️", "👍", "👎", "😂", "‼️", "❓")

    data class Parsed(val body: String, val react: String?, val replyTo: String?)

    private val KEYS = listOf("[[react:", "[[reply:")

    fun parse(text: String): Parsed {
        var rest = text.trimStart()
        var react: String? = null
        var reply: String? = null
        while (rest.startsWith("[[")) {
            val end = rest.indexOf("]]")
            if (end < 0) {
                // While an answer streams, a line that is still being written stays hidden
                val partial = if (rest.length < 8) KEYS.any { it.startsWith(rest) } else KEYS.any { rest.startsWith(it) }
                return Parsed(if (partial) "" else rest, react, reply)
            }
            val inner = rest.substring(2, end)
            val value = inner.substringAfter(':', "").trim()
            when (inner.substringBefore(':', "").trim().lowercase()) {
                "react" -> react = reaction(value) ?: react
                "reply" -> reply = value.trim('"', '“', '”', '\'').trim().take(QuoteMessage.MAX_CHARS).ifBlank { null } ?: reply
                else -> break
            }
            rest = rest.substring(end + 2).trimStart()
        }
        return Parsed(rest, react, reply)
    }

    /** The answer without the leading lines. */
    fun strip(text: String): String = parse(text).body

    /** The canonical form of [emoji] when it is one of [REACTIONS] (with or without the emoji variation mark), else null. */
    fun reaction(emoji: String): String? {
        val bare = emoji.trim().replace("️", "")
        return REACTIONS.firstOrNull { it.replace("️", "") == bare }
    }
}
