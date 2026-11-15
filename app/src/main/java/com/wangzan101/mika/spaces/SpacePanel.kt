package com.wangzan101.mika.spaces

import com.wangzan101.mika.cap.sandbox.SandboxDirs
import com.wangzan101.mika.core.workspace.SandboxPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The page a space shows and the messages that build and refresh it. Orbit writes one self-contained
 * HTML file into the space's own workspace; the space screen renders that file in a sandboxed WebView
 * (scripts on, no network, no file access), so a panel can be interactive without reaching anything else.
 */
object SpacePanel {
    /** Workspace-relative name of the page; the agent writes it at /workspace/space.html. */
    const val FILE = "space.html"
    /** Pages bigger than this are not shown (a panel is a small page, not a site). */
    const val MAX_BYTES = 2L * 1024 * 1024

    val agentPath: String get() = "${SandboxPaths.WORKSPACE}/$FILE"

    /** The first message in a new space's conversation: what to build and the rules the page follows. */
    fun buildPrompt(space: Space): String = """
        Build a space for me: "${space.description}"

        A space is a small live panel I keep in my sidebar, like a tracker, a dashboard or a set of controls.
        Write it as one self-contained HTML file at $agentPath and overwrite that file whenever it changes.
        ${rules()}
        When the page is written, tell me in one or two sentences what is on it and what I can ask you to change.
    """.trimIndent()

    /** Sent when the user taps Refresh, or by the scheduled refresh. */
    fun refreshPrompt(space: Space): String = """
        Refresh my space "${space.name}" ($agentPath): gather the current information it shows with your tools,
        update the page with it, and keep the layout and anything I changed by hand on it.
        ${rules()}
        Reply in one sentence with what changed, or say nothing changed.
    """.trimIndent()

    private fun rules() = """
        Page rules: inline all CSS and JavaScript, load nothing from the network, and fit a phone screen (about 380px wide).
        Use a true black background (#000) with light text, rounded cards, system fonts and generous spacing.
        Put the data in the page itself, show when it was last updated, and keep checkboxes or notes the user can change in localStorage.
    """.trimIndent()

    /** The page as it is now, or null when Orbit has not written it yet (or it is too big or unreadable). */
    suspend fun read(dirs: SandboxDirs, space: Space): String? = withContext(Dispatchers.IO) {
        runCatching {
            dirs.gate.openRead(dirs.workspace(space.conversationId), FILE).use { file ->
                if (file.size > MAX_BYTES) null else file.input().readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }
}
