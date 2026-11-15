package com.wangzan101.mika.spaces

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceStoreTest {
    private fun tempFile(): File = File(Files.createTempDirectory("spaces").toFile(), "spaces.json")

    @Test fun createsFromASentenceAndSurvivesARestart() {
        val file = tempFile()
        var clock = 1_000L
        val store = SpaceStore(file) { clock++ }
        val space = store.create("Build me a VM control dashboard for my cloud servers")
        assertEquals("VM control dashboard", space.name)
        assertEquals("🖥️", space.emoji)
        val reread = SpaceStore(file)
        assertEquals(listOf(space), reread.spaces.value)
        assertEquals(space, reread.forConversation(space.conversationId))
    }

    @Test fun pinnedComeFirstThenNewest() {
        var clock = 1L
        val store = SpaceStore(tempFile()) { clock++ }
        val a = store.create("Habit tracker")
        val b = store.create("Budget board")
        assertEquals(listOf(b.id, a.id), store.spaces.value.map { it.id })
        assertTrue(store.setPinned(a.id, true))
        assertEquals(listOf(a.id, b.id), store.spaces.value.map { it.id })
    }

    @Test fun editsKeepRulesAndDeleteRemoves() {
        val store = SpaceStore(tempFile())
        val s = store.create("Reading list", name = "Books", emoji = "📚")
        assertEquals("Books", s.name)
        assertFalse(store.rename(s.id, "   "))
        assertTrue(store.rename(s.id, "Shelf"))
        assertTrue(store.setRefreshPlan(s.id, "plan-1"))
        assertEquals("plan-1", store.get(s.id)?.refreshPlanId)
        assertEquals("Shelf", store.delete(s.id)?.name)
        assertNull(store.get(s.id))
        assertTrue(store.spaces.value.isEmpty())
    }

    @Test fun promptsCarryTheSentenceAndThePagePath() {
        val s = SpaceStore(tempFile()).create("Weather for my commute")
        assertTrue(SpacePanel.buildPrompt(s).contains("Weather for my commute"))
        assertTrue(SpacePanel.buildPrompt(s).contains("/workspace/space.html"))
        assertTrue(SpacePanel.refreshPrompt(s).contains(s.name))
        assertEquals("⛅", s.emoji)
    }
}
