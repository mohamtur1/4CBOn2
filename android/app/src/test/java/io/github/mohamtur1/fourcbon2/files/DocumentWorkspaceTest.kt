package io.github.mohamtur1.fourcbon2.files

import io.github.mohamtur1.fourcbon2.testutil.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DocumentWorkspaceTest {

    @Test
    fun importReadExportRoundTrip() {
        val workspace = DocumentWorkspace(createTempDirectory("workspace"))
        workspace.importText("note.md", "hello world")
        assertEquals("hello world", workspace.readText("note.md"))
        assertEquals("hello world", workspace.exportText("note.md"))
        assertEquals(listOf("note.md"), workspace.listWorkspaceFiles())
    }

    @Test
    fun pathTraversalIsRejected() {
        val workspace = DocumentWorkspace(createTempDirectory("workspace-traversal"))
        assertThrows(IllegalArgumentException::class.java) {
            workspace.resolve("../escape.txt")
        }
    }

    @Test
    fun importOverExistingFileIsRejected() {
        val workspace = DocumentWorkspace(createTempDirectory("workspace-dup"))
        workspace.importText("note.md", "first")
        assertThrows(IllegalArgumentException::class.java) {
            workspace.importText("note.md", "second")
        }
    }

    @Test
    fun readingMissingFileIsRejected() {
        val workspace = DocumentWorkspace(createTempDirectory("workspace-missing"))
        assertThrows(IllegalArgumentException::class.java) {
            workspace.readText("missing.txt")
        }
    }
}
