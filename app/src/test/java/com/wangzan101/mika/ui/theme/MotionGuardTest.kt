package com.wangzan101.mika.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps every animation on the one motion system: outside `ui/theme/Motion.kt` no source file may build its own
 * tween, spring, keyframes or infinite repeat, or call a Compose animation with its default (untokened) spec.
 */
class MotionGuardTest {
    private val sources = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
        .filterNot { it.invariantSeparatorsPath.endsWith("ui/theme/Motion.kt") }.toList()

    private val adHoc = Regex("""(?<![\w.])(tween|spring|keyframes|infiniteRepeatable|repeatable)\s*(<[^>]*>)?\(""")
    private val untokened = Regex("""\b(fadeIn|fadeOut|expandVertically|shrinkVertically|expandHorizontally|shrinkHorizontally|scaleIn|scaleOut|slideInVertically|slideOutVertically|animateContentSize)\(\)""")

    @Test
    fun sourcesFound() = assertTrue("no sources under ${File(".").absolutePath}", sources.size > 100)

    @Test
    fun noAnimationBringsItsOwnTiming() {
        val hits = sources.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val code = line.substringBefore("//")
                if (code.trimStart().startsWith("*") || code.trimStart().startsWith("import ")) null
                else if (adHoc.containsMatchIn(code) || untokened.containsMatchIn(code)) "${f.path}:${i + 1}: ${line.trim()}" else null
            }
        }
        assertTrue("Use MikaTheme.motion / MikaMotion instead:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
