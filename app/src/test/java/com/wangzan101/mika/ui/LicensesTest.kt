package com.wangzan101.mika.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LicensesTest {
    /** 单元测试的工作目录是 app/;仓库根目录在上一级。 */
    private val notices = File("../licenses/THIRD_PARTY_NOTICES.md").takeIf { it.isFile } ?: File("licenses/THIRD_PARTY_NOTICES.md")

    @Test
    fun everyNoticeEntryShown() {
        val headings = notices.readLines().filter { it.startsWith("## ") }.map { it.removePrefix("## ").trim() }
        assertTrue("notices has headings", headings.size >= 16)
        val shown = Licenses.ENTRIES.map { it.name }
        headings.forEach { assertTrue("missing on licenses screen: $it", it in shown) }
        assertEquals("Orbit", shown.first())
        assertEquals("Mika", shown[1])
    }

    /** The screen reads in English with Orbit's name; no Chinese is left in what it shows. */
    @Test
    fun englishThroughout() {
        val cjk = Regex("""[\u3400-\u9FFF]""")
        (Licenses.ENTRIES.flatMap { listOf(it.name, it.license, it.note) } + Licenses.GPL_SOURCE_OFFER + Licenses.RUNTIME_NOTE)
            .forEach { assertTrue(it, !cjk.containsMatchIn(it)) }
    }

    @Test
    fun gplSourceOfferPresent() {
        val byName = Licenses.ENTRIES.associateBy { it.name }
        assertEquals("Apache-2.0", byName.getValue("Orbit").license)
        assertEquals("Apache-2.0", byName.getValue("Mika").license)
        assertTrue(byName.getValue("Mika").note.contains("https://github.com/wangzan101/Mika"))
        assertEquals("GPL-2.0-or-later", byName.getValue("PRoot").license)
        assertEquals("LGPL-3.0-or-later", byName.getValue("talloc").license)
        assertEquals("Apache-2.0", byName.getValue("Apache Commons Compress").license)
        listOf("OkHttp", "Okio", "kotlinx.coroutines", "kotlinx.serialization", "Kotlin standard library", "AndroidX (including Jetpack Compose)")
            .forEach { assertEquals(it, "Apache-2.0", byName.getValue(it).license) }
        assertTrue(Licenses.GPL_SOURCE_OFFER.contains("gpl-sources.tar.gz"))
        assertTrue(Licenses.GPL_SOURCE_OFFER.contains("PRoot") && Licenses.GPL_SOURCE_OFFER.contains("talloc"))
        assertTrue(byName.getValue("Alpine Linux").note.contains("gpl-sources.tar.gz"))
        assertEquals("0BSD", byName.getValue("XZ for Java").license)
    }

    /** 「开源许可」页逐个列出自带运行环境里的软件包,每个都有许可证。 */
    @Test
    fun runtimePackagesListed() {
        val manifest = File("src/main/assets/runtime/packages.json")
        val entries = Licenses.runtimePackages(manifest.readText())
        assertTrue(entries.size > 40)
        listOf("busybox", "musl", "python3", "nodejs", "py3-pillow", "py3-pypdf").forEach { n -> assertTrue(n, entries.any { it.name == n }) }
        entries.forEach { assertTrue(it.name, it.license.isNotBlank() && it.note.startsWith("Version ")) }
    }
}
