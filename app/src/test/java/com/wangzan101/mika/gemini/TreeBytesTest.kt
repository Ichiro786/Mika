package com.wangzan101.mika.gemini

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/** The Gemini engine's "space used" figure must never take the models page down, whatever the Linux tree holds. */
class TreeBytesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun countsRegularFilesAndDoesNotFollowLinks() {
        val root = tmp.newFolder("gm")
        File(root, "a.bin").writeBytes(ByteArray(100))
        File(root, "sub").mkdirs()
        File(root, "sub/b.bin").writeBytes(ByteArray(40))
        val outside = tmp.newFile("outside.bin").apply { writeBytes(ByteArray(5000)) }
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        assertEquals(140L, treeBytes(root))
    }

    @Test
    fun aMissingRootIsZero() {
        assertEquals(0L, treeBytes(File(tmp.root, "nope")))
    }

    @Test
    fun foldersThatCannotBeListedAndBrokenLinksAreSkipped() {
        val root = tmp.newFolder("gm")
        File(root, "ok.bin").writeBytes(ByteArray(10))
        // The Linux inside sets its own permissions: a folder the app cannot list. (Empty, so the total is the
        // same whether or not this test runs as a user the permission applies to.)
        val locked = File(root, "locked").apply { mkdirs() }
        locked.setReadable(false, false)
        locked.setExecutable(false, false)
        // A link whose target went away, as when the server removes a file while the walk is under way.
        Files.createSymbolicLink(File(root, "gone").toPath(), File(root, "was-here").toPath())
        try {
            assertEquals(10L, treeBytes(root))
        } finally {
            locked.setReadable(true, false)
            locked.setExecutable(true, false)
        }
    }
}
