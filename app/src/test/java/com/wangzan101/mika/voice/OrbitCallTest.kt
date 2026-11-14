package com.wangzan101.mika.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitCallTest {
    @Test fun turnEndsAfterSpeechAndAQuietGap() {
        val end = EndOfSpeech()
        // Silence before speaking never ends the turn.
        for (t in 0L..3_000L step 100) assertFalse(end.feed(0.01f, t))
        // A short click is not speech.
        assertFalse(end.feed(0.5f, 3_100))
        assertFalse(end.feed(0.01f, 3_200))
        for (t in 3_300L..3_800L step 100) assertFalse(end.feed(0.4f, t))
        assertFalse(end.feed(0.02f, 3_900))
        assertFalse(end.feed(0.02f, 4_900))
        assertTrue(end.feed(0.02f, 5_100))
    }

    @Test fun repliesAreReadWithoutMarkdown() {
        val md = "## Plan\n\n- **Book** the [table](https://example.com)\n- Run `ls`\n\n```kotlin\nval a = 1\n```\nDone."
        assertEquals("Plan\nBook the table\nRun ls\nDone.", spokenText(md))
    }

    @Test fun longRepliesStopAtASentence() {
        val text = "One sentence here. ".repeat(200)
        val spoken = spokenText(text, max = 100)
        assertTrue(spoken.length <= 100)
        assertTrue(spoken.endsWith("."))
    }
}
