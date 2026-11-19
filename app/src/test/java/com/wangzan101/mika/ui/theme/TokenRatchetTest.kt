package com.wangzan101.mika.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screens take spacing, radii, type and colour from the theme. Outside `ui/theme` no file may hold more raw dp
 * (above 2dp), sp or `Color(0x…)` values than `token-ratchet.txt` records, and a file not listed there must
 * have none. After moving a screen onto the tokens, run `tools/ui/token_baseline.py` so the lower counts stick.
 */
class TokenRatchetTest {
    private val root = File("src/main/java/com/wangzan101/mika")
    private val dp = Regex("""(?<![\w.])(\d+(?:\.\d+)?)\.dp\b""")
    private val sp = Regex("""(?<![\w.])\d+(?:\.\d+)?\.sp\b""")
    private val hex = Regex("""Color\(0x""")

    private fun counts(f: File): Triple<Int, Int, Int> {
        var d = 0; var s = 0; var h = 0
        f.forEachLine { line ->
            val code = line.substringBefore("//")
            if (code.trimStart().startsWith("*")) return@forEachLine
            d += dp.findAll(code).count { it.groupValues[1].toDouble() > 2 }
            s += sp.findAll(code).count()
            h += hex.findAll(code).count()
        }
        return Triple(d, s, h)
    }

    @Test
    fun rawDesignValuesOnlyGoDown() {
        val baseline = javaClass.classLoader!!.getResource("token-ratchet.txt")!!.readText().lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { l -> l.split(" ").let { it[0] to Triple(it[1].toInt(), it[2].toInt(), it[3].toInt()) } }
        val over = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.mapNotNull { f ->
            val rel = f.relativeTo(root).invariantSeparatorsPath
            if (rel.startsWith("ui/theme/")) return@mapNotNull null
            val now = counts(f)
            val max = baseline[rel] ?: Triple(0, 0, 0)
            if (now.first > max.first || now.second > max.second || now.third > max.third) "$rel: now $now, allowed $max" else null
        }.toList()
        assertTrue("Use MikaSpace/MikaRadius/MikaSize/MikaType/MikaTheme.colors instead of raw values:\n" + over.joinToString("\n"), over.isEmpty())
    }
}
