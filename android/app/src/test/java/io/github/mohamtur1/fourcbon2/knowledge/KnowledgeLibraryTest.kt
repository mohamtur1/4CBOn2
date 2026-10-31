package io.github.mohamtur1.fourcbon2.knowledge

import io.github.mohamtur1.fourcbon2.local.KnowledgeEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeLibraryTest {

    private fun entry(id: String, title: String, content: String) = KnowledgeEntry(
        id = id,
        title = title,
        source = "source-$id",
        provenance = "provenance-$id",
        content = content,
        updatedAtEpochMs = 1000,
    )

    @Test
    fun saveAndRead() {
        val library = KnowledgeLibrary()
        library.save(entry("e1", "Kaggle rules", "submissions close at midnight"))
        assertEquals("Kaggle rules", library.read("e1")?.title)
        assertEquals(1, library.all().size)
    }

    @Test
    fun searchReturnsProvenanceAndUpdateTime() {
        val library = KnowledgeLibrary()
        library.save(entry("e1", "Kaggle competition", "deadline info"))
        library.save(entry("e2", "GitHub notes", "branch protection"))
        val results = library.search("Kaggle")
        assertEquals(1, results.size)
        assertEquals("source-e1", results[0].source)
        assertEquals(1000L, results[0].updatedAtEpochMs)
    }

    @Test
    fun emptyQueryReturnsNothing() {
        val library = KnowledgeLibrary()
        library.save(entry("e1", "Anything", "content"))
        assertTrue(library.search("").isEmpty())
    }

    @Test
    fun updateTimeIsExposed() {
        val library = KnowledgeLibrary(clock = { 5000 })
        library.save(entry("e1", "T", "C"))
        assertEquals(5000L, library.updateTime("e1"))
    }
}
