package com.wangzan101.mika.chat

import com.wangzan101.mika.core.agent.Turn
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SideChatPersistenceTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun emptySideChatRemainsVisibleAfterRestartWithoutChangingOldHistory() {
        val dir = tmp.newFolder()
        val store = ConversationStore(dir)
        store.append("parent", Turn("Parent question", "Parent answer", "succeeded"))
        File(dir, "side.workspace").writeText("""{"kind":"side","createdAt":123,"parent":"parent","parentTurn":1}""")

        val reopened = ConversationStore(dir)
        assertEquals(setOf("parent", "side"), reopened.list().map { it.id }.toSet())
        assertTrue(reopened.turns("side").isEmpty())
        assertEquals("Parent question", reopened.turns("parent").single().userText)
    }

    @Test fun deletingSideChatRemovesItsMetadataButNotTheParent() {
        val dir = tmp.newFolder()
        val store = ConversationStore(dir)
        store.append("parent", Turn("Parent question", "Parent answer", "succeeded"))
        store.append("side", Turn("Side question", "Side answer", "succeeded"))
        val metadata = File(dir, "side.workspace")
        metadata.writeText("""{"kind":"side","createdAt":123,"parent":"parent","parentTurn":1}""")

        store.delete("side")
        assertFalse(metadata.exists())
        assertTrue(store.turns("side").isEmpty())
        assertEquals("Parent question", ConversationStore(dir).turns("parent").single().userText)
    }

    @Test fun creatingSideChatsStoresOriginAndKeepsTranscriptsIndependent() {
        val dir = tmp.newFolder()
        val store = ConversationStore(dir, clock = { 1234L })
        store.append("parent", Turn("Original", "Answer", "succeeded"))
        assertTrue(store.createSideChat("side", "parent", 1, "parent"))
        store.append("side", Turn("Side question", "Side answer", "succeeded"))

        val reopened = ConversationStore(dir)
        assertEquals(SideChatOrigin(1234L, "parent", 1, "parent"), reopened.sideChat("side"))
        assertEquals("Original", reopened.turns("parent").single().userText)
        assertEquals("Side question", reopened.turns("side").single().userText)
        assertEquals("side", reopened.list().single { it.sideChat != null }.id)
    }

    @Test fun invalidWorkspaceMetadataDoesNotHideLegacyConversations() {
        val dir = tmp.newFolder()
        val store = ConversationStore(dir)
        store.append("old", Turn("Existing", "Answer", "succeeded"))
        File(dir, "old.workspace").writeText("broken")
        assertEquals("old", ConversationStore(dir).list().single().id)
        assertEquals(null, ConversationStore(dir).sideChat("old"))
        assertFalse(store.createSideChat("../unsafe", "old", 1, "old"))
    }


    @Test fun sideChatsSortByActualActivityIncludingNewEmptyChats() {
        val dir = tmp.newFolder()
        var now = 10L
        val store = ConversationStore(dir, clock = { now })
        assertTrue(store.createSideChat("a", null, null, null))
        now = 20L
        assertTrue(store.createSideChat("b", null, null, null))
        now = 30L
        assertTrue(store.createSideChat("c", null, null, null))
        assertEquals(listOf("c", "b", "a"), store.list().map { it.id })
        store.append("a", Turn("New activity", "Answer", "succeeded"))
        assertEquals(listOf("a", "c"), ConversationStore(dir).list().take(2).map { it.id })
    }

}
