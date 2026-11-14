package com.wangzan101.mika.chat

import com.wangzan101.mika.core.agent.Turn
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChannelWorkspaceTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun emptyChannelAndItsMetadataSurviveRestart() {
        val dir = tmp.newFolder()
        val store = ConversationStore(dir, clock = { 123L })
        assertTrue(store.createChannel("project", "Android", "Release work", "main"))
        val reopened = ConversationStore(dir)
        assertEquals(WorkspaceChannel("Android", "Release work", 123L, "main"), reopened.channel("project"))
        val entry = reopened.list().single()
        assertEquals("project", entry.id)
        assertEquals("Android", entry.title)
        assertTrue(reopened.turns("project").isEmpty())
    }

    @Test fun editingAndRemovingChannelKeepsAllConversationData() {
        val dir = tmp.newFolder()
        val store = ConversationStore(dir, clock = { 456L })
        store.append("main", Turn("Main", "Answer", "succeeded"))
        assertTrue(store.createChannel("project", "Old", "", "main"))
        store.append("project", Turn("Project", "Answer", "succeeded"))
        val unrelated = File(dir, "shared.txt").apply { writeText("keep") }
        assertTrue(store.updateChannel("project", "New", "Notes"))
        assertEquals("New", ConversationStore(dir).list().first { it.id == "project" }.title)
        assertTrue(store.removeChannel("project"))
        val reopened = ConversationStore(dir)
        assertNull(reopened.channel("project"))
        assertEquals("Project", reopened.turns("project").single().userText)
        assertEquals("Main", reopened.turns("main").single().userText)
        assertTrue(unrelated.isFile)
        assertTrue(reopened.list().any { it.id == "project" && it.channel == null })
    }

    @Test fun channelRejectsInvalidNamesAndCannotOverwriteExistingHistory() {
        val dir = tmp.newFolder()
        val store = ConversationStore(dir)
        store.append("existing", Turn("Existing", "Answer", "succeeded"))
        assertFalse(store.createChannel("existing", "Project", "", null))
        assertFalse(store.createChannel("empty", "  ", "", null))
        assertFalse(store.createChannel("../escape", "Project", "", null))
        assertEquals("Existing", store.turns("existing").single().userText)
    }
}
