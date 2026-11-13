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
}
