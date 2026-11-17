package com.wangzan101.mika.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A schedule file that cannot be read is never lost silently: a copy is kept and the store says so. */
class UnreadableScheduleFileTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun aBrokenFileIsCopiedAsideAndReported() {
        val file = File(tmp.root, "triggers-v3.json").apply { writeText("{ not json") }
        val store = FileTriggerStore(file)
        val copy = store.unreadableCopy
        assertNotNull(copy)
        assertEquals("{ not json", copy!!.readText())
        assertTrue(copy.name.startsWith("triggers-v3.json.corrupt-"))
        // The list starts empty, as before.
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun aHealthyOrMissingFileReportsNothing() {
        assertNull(FileTriggerStore(File(tmp.root, "none.json")).unreadableCopy)
    }

    @Test
    fun copiesNeverOverwriteEachOtherAndOnlyTheNewestFewStay() {
        val file = File(tmp.root, "t.json")
        repeat(UNREADABLE_KEPT + 3) { i ->
            file.writeText("broken $i")
            keepUnreadable(file, now = 1_000L + i)
        }
        val copies = tmp.root.listFiles { f -> f.name.startsWith("t.json.corrupt-") }!!.sortedBy { it.name }
        assertEquals(UNREADABLE_KEPT, copies.size)
        // The newest is there with its own content; the oldest three are gone.
        assertEquals("broken ${UNREADABLE_KEPT + 2}", File(tmp.root, "t.json.corrupt-${1_000L + UNREADABLE_KEPT + 2}").readText())
        assertTrue(copies.none { it.name.endsWith("-1000") })
    }
}
