package io.github.mohamtur1.fourcbon2.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionDiffTest {

    @Test
    fun detectsAddedRemovedAndEqualLines() {
        val diff = VersionDiff.diff("a\nb\nc", "a\nx\nc")
        assertEquals(1, diff.removedLines)
        assertEquals(1, diff.addedLines)
        val removed = diff.lines.filter { it.kind == VersionDiff.Kind.REMOVED }.map { it.text }
        val added = diff.lines.filter { it.kind == VersionDiff.Kind.ADDED }.map { it.text }
        assertEquals(listOf("b"), removed)
        assertEquals(listOf("x"), added)
    }

    @Test
    fun identicalContentHasNoChanges() {
        val diff = VersionDiff.diff("a\nb", "a\nb")
        assertEquals(0, diff.removedLines)
        assertEquals(0, diff.addedLines)
    }

    @Test
    fun exportableChangeRequiresApproval() {
        val diff = VersionDiff.diff("old", "new")
        val change = VersionDiff.export("notebook.ipynb", "rev-1", "rev-2", diff)
        assertTrue(change.requiresApproval)
        assertTrue(change.patch.contains("-old"))
        assertTrue(change.patch.contains("+new"))
        assertTrue(change.summary.contains("1 added"))
    }
}
