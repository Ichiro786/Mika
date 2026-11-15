package com.wangzan101.mika.core.agent

import com.wangzan101.mika.core.model.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMarkupTest {
    @Test fun leadingLinesBecomeReactionAndQuote() {
        val p = AnswerMarkup.parse("[[react:👍]]\n[[reply:\"book the 7pm table\"]]\nDone, it's booked.")
        assertEquals("👍", p.react)
        assertEquals("book the 7pm table", p.replyTo)
        assertEquals("Done, it's booked.", p.body)
    }

    @Test fun reactionWithoutVariationMarkIsCanonical() {
        assertEquals("❤️", AnswerMarkup.parse("[[react:❤]] Thanks!").react)
        assertNull(AnswerMarkup.parse("[[react:🐱]] Hi").react)
    }

    @Test fun plainAnswersAndOtherBracketsStayAsTheyAre() {
        assertEquals("Hello", AnswerMarkup.strip("Hello"))
        assertEquals("[[note]] keep", AnswerMarkup.strip("[[note]] keep"))
    }

    @Test fun aLineStillStreamingIsHidden() {
        assertEquals("", AnswerMarkup.strip("[[rea"))
        assertEquals("", AnswerMarkup.strip("[[react:👍"))
        assertEquals("Sure", AnswerMarkup.strip("[[react:👍]] Sure"))
    }

    @Test fun userReactionReachesTheNextTurn() {
        val msg = Conversation.context(listOf(Turn("hi", "Hello!", "succeeded", reaction = "❤️")))!!
        val text = (msg.parts.single() as Part.Text).text
        assertTrue(text.contains("the user reacted ❤️ to Mika's reply"))
    }
}
