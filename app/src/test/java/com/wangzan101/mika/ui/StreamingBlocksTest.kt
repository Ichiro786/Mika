package com.wangzan101.mika.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingBlocksTest {
    @Test fun splitsOnlyBetweenFinishedTopLevelBlocks() {
        assertEquals(0, StreamingMarkdown.stableEnd("Just one growing paragraph"))
        // The line still being written never counts; a finished line after the gap does.
        assertEquals(0, StreamingMarkdown.stableEnd("# Title\n\nSecond para"))
        assertEquals("# Title\n\n".length, StreamingMarkdown.stableEnd("# Title\n\nSecond para\n"))
        // A gap inside a list, a code fence or a quote is not a boundary.
        assertEquals(0, StreamingMarkdown.stableEnd("- one\n\n- two\n\nmore"))
        assertEquals(0, StreamingMarkdown.stableEnd("```\ncode\n\nstill code\n"))
        assertEquals(0, StreamingMarkdown.stableEnd("> quote\n\n> more"))
        // After the fence closes, the next paragraph starts a reusable boundary.
        val fenced = "```\ncode\n\nstill code\n```\n\nAfter\n"
        assertEquals(fenced.length - "After\n".length, StreamingMarkdown.stableEnd(fenced))
    }

    @Test fun reusedPrefixParsesLikeTheWholeReply() {
        val cache = StreamingBlocks()
        val reply = "# Plan\n\nFirst **step** done.\n\n```kotlin\nval a = 1\n\nval b = 2\n```\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\nLast line with [a link](https://example.com"
        // Feed the reply as it would stream, a few characters at a time, and compare every frame.
        var i = 0
        while (i < reply.length) {
            i = minOf(reply.length, i + 7)
            val text = reply.substring(0, i)
            assertEquals(text, MarkdownModel.parseStreaming(text), cache.parse(text))
        }
    }
}
