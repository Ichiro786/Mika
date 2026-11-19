package com.wangzan101.mika.ui

import com.wangzan101.mika.core.assembly.knownTools
import com.wangzan101.mika.i18n.ChineseTexts
import com.wangzan101.mika.i18n.ResFileTexts
import com.wangzan101.mika.i18n.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ToolLabelsTest {
    @get:Rule val texts = ChineseTexts()

    private val cjk = Regex("""[㐀-鿿]""")

    /** Every tool is mapped by its stable English name, and agrees with its declared label's resource. */
    @Test
    fun everyToolIsMappedByName() {
        val tools = knownTools()
        assertTrue(tools.names.size > 100)
        for (name in tools.names) {
            val res = ToolLabels.TOOL_NAMES[name]
            assertTrue("$name has no entry in TOOL_NAMES", res != null)
            tools[name]?.label?.let { declared ->
                ToolLabels.TOOL_LABELS[declared]?.let { assertEquals("$name ($declared)", it, res) }
            }
        }
    }

    /** Old records that stored a label still read in the user's language. */
    @Test
    fun declaredLabelsStillLocalize() {
        val tools = knownTools()
        val labels = ToolLabels(tools)
        val declared = tools.names.mapNotNull { n -> tools[n]?.label?.let { n to it } }
        for ((name, label) in declared) {
            if (label !in ToolLabels.TOOL_LABELS) continue
            val display = if (name == "mika_open_screen") "打开 Orbit 的页面" else label
            assertEquals(name, display, labels.label(name))
        }
    }

    /** In English no tool, known, renamed or brand new, ever shows Chinese. */
    @Test
    fun englishNeverShowsChinese() {
        UiText.source = ResFileTexts.english
        val tools = knownTools()
        val labels = ToolLabels(tools)
        for (name in tools.names) assertFalse("$name: ${labels.label(name)}", cjk.containsMatchIn(labels.label(name)))
        assertEquals("Tap", labels.label("tap"))
        assertEquals("Open web page", labels.label("browser_navigate"))
        assertEquals("Brand new thing", labels.label("phone_brand_new_thing"))
        assertEquals("Mystery tool", labels.label("mystery_tool"))
    }
}
