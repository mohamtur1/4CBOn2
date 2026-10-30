package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalKnowledgeItemTest {
    @Test
    fun acceptsBoundedValuesAndAnEmptyBody() {
        val item = syntheticItem()
        assertEquals("report-1", item.id)
        assertEquals("Synthetic report", item.title)
        assertEquals("Evidence body.", item.body)
        assertEquals("Synthetic source", item.sourceLabel)
        assertEquals(KnowledgeProvenance.OWNER_SUPPLIED, item.provenance)

        assertEquals("", syntheticItem(body = "").body)
    }

    @Test
    fun rejectsMalformedIdentifiersAndOutOfRangeTimestamps() {
        listOf("", "has space", "a".repeat(65), "bad/slash", "tab\there").forEach { badId ->
            assertThrows(IllegalArgumentException::class.java) { syntheticItem(id = badId) }
        }
        listOf(0L, -1L).forEach { badTime ->
            assertThrows(IllegalArgumentException::class.java) { syntheticItem(savedAt = badTime) }
        }
        assertTrue(Regex("[A-Za-z0-9._:-]{1,64}").matches("a".repeat(64)))
    }

    @Test
    fun rejectsBlankOrOversizedTextAndControlSeparators() {
        assertThrows(IllegalArgumentException::class.java) { syntheticItem(title = "   ") }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticItem(title = "a".repeat(LocalKnowledgeItem.MAX_TITLE_LENGTH + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticItem(body = "a".repeat(LocalKnowledgeItem.MAX_BODY_LENGTH + 1))
        }
        assertThrows(IllegalArgumentException::class.java) { syntheticItem(sourceLabel = "") }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticItem(sourceLabel = "a".repeat(LocalKnowledgeItem.MAX_SOURCE_LABEL_LENGTH + 1))
        }
        assertThrows(IllegalArgumentException::class.java) { syntheticItem(title = "line\nbreak") }
        assertThrows(IllegalArgumentException::class.java) { syntheticItem(body = "tab\there") }
        assertThrows(IllegalArgumentException::class.java) { syntheticItem(sourceLabel = "carriage\rreturn") }
    }

    @Test
    fun provenanceValuesDescribeOnlyRealLocalOrigins() {
        assertEquals(
            listOf(
                "OWNER_SUPPLIED",
                "OWNER_OBSERVED",
                "LOCAL_DRAFT_DERIVED",
            ),
            KnowledgeProvenance.entries.map { it.name },
        )

        // No value may imply a service response or an invented citation.
        val names = KnowledgeProvenance.entries.map { it.name }
        listOf("SERVICE_RESPONSE", "MODEL_OUTPUT", "CITATION", "VERIFIED", "TRUSTED").forEach { forbidden ->
            assertFalse("provenance must not claim $forbidden", names.contains(forbidden))
        }

        val observed = syntheticItem(provenance = KnowledgeProvenance.OWNER_OBSERVED)
        assertEquals(KnowledgeProvenance.OWNER_OBSERVED, observed.provenance)
    }

    @Test
    fun matchingIsCaseInsensitiveAcrossVisibleFieldsOnly() {
        val item = syntheticItem(
            id = "Match-Case",
            title = "Quarterly Summary",
            body = "Revenue notes for the synthetic example.",
            sourceLabel = "Owner Notebook",
        )

        assertTrue(item.matches(""))
        assertTrue(item.matches("   "))
        assertTrue(item.matches("QUARTERLY"))
        assertTrue(item.matches("quarterly summary"))
        assertTrue(item.matches("revenue notes"))
        assertTrue(item.matches("  owner notebook  "))
        assertFalse(item.matches("credential"))
        assertFalse(item.matches("nonexistent phrase"))

        // The identifier is not owner-visible text, so it is not searched.
        assertFalse(item.matches("Match-Case"))
        assertFalse(item.matches("match-case"))
    }

    @Test
    fun searchBoundsAreBoundedAndSane() {
        assertEquals(20, KnowledgeSearchBounds.DEFAULT_LIMIT)
        assertEquals(50, KnowledgeSearchBounds.MAX_LIMIT)
        assertEquals(120, KnowledgeSearchBounds.MAX_QUERY_LENGTH)
        assertTrue(KnowledgeSearchBounds.DEFAULT_LIMIT <= KnowledgeSearchBounds.MAX_LIMIT)
    }

    private fun syntheticItem(
        id: String = "report-1",
        title: String = "Synthetic report",
        body: String = "Evidence body.",
        sourceLabel: String = "Synthetic source",
        provenance: KnowledgeProvenance = KnowledgeProvenance.OWNER_SUPPLIED,
        savedAt: Long = 1_700_000_000_000L,
    ) = LocalKnowledgeItem(
        id = id,
        title = title,
        body = body,
        sourceLabel = sourceLabel,
        provenance = provenance,
        savedAtEpochMillis = savedAt,
    )
}
