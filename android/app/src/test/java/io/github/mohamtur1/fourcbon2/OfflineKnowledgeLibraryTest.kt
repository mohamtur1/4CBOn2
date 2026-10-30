package io.github.mohamtur1.fourcbon2

import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineKnowledgeLibraryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private var now = 1_700_000_000_000L

    @Test
    fun savedItemSurvivesCloseAndIndependentReopen() {
        val databaseFile = File(temporaryFolder.newFolder(), "knowledge.db")

        val firstDatabase = LocalEntityDatabase(databaseFile)
        firstDatabase.open()
        val firstLibrary = OfflineKnowledgeLibrary(firstDatabase, { now })
        val saved = firstLibrary.saveItem(
            id = "report-1",
            title = "Synthetic quarterly summary",
            body = "Revenue notes for the synthetic example.",
            sourceLabel = "Owner Notebook",
            provenance = KnowledgeProvenance.OWNER_SUPPLIED,
        ) as OfflineKnowledgeLibrary.SaveResult.Saved
        firstDatabase.close()

        assertTrue(databaseFile.isFile)
        // The body must not appear in cleartext on disk.
        assertFalse(databaseFile.readText(Charsets.UTF_8).contains("Revenue notes"))

        val reopenedDatabase = LocalEntityDatabase(databaseFile)
        reopenedDatabase.open()
        try {
            val reopenedLibrary = OfflineKnowledgeLibrary(reopenedDatabase, { now })
            val loaded = reopenedLibrary.loadItem("report-1") as OfflineKnowledgeLibrary.ReadResult.Found
            assertEquals(saved.item, loaded.item)
            assertEquals("Revenue notes for the synthetic example.", loaded.item.body)
            assertEquals(KnowledgeProvenance.OWNER_SUPPLIED, loaded.item.provenance)
            assertEquals(listOf("report-1"), reopenedLibrary.allItems().map { it.id })
        } finally {
            reopenedDatabase.close()
        }
    }

    @Test
    fun saveRefusesDuplicateIdentifierWithoutOverwriting() {
        withLibrary { library, _, _ ->
            library.saveItem("report-1", "First", "First body.", "source-a", KnowledgeProvenance.OWNER_SUPPLIED)

            val refused = library.saveItem(
                "report-1", "Second", "Second body.", "source-b", KnowledgeProvenance.OWNER_OBSERVED,
            ) as OfflineKnowledgeLibrary.SaveResult.Refused

            assertEquals(KnowledgeRefusal.DUPLICATE_ITEM_ID, refused.reason)
            val existing = library.loadItem("report-1") as OfflineKnowledgeLibrary.ReadResult.Found
            assertEquals("First", existing.item.title)
        }
    }

    @Test
    fun loadReportsUnknownItemExplicitly() {
        withLibrary { library, _, _ ->
            assertEquals(
                OfflineKnowledgeLibrary.ReadResult.Refused("missing", KnowledgeRefusal.UNKNOWN_ITEM),
                library.loadItem("missing"),
            )
            assertNull(library.allItems().firstOrNull())
        }
    }

    @Test
    fun searchIsCaseInsensitiveAndDeterministicallyOrdered() {
        withLibrary { library, _, _ ->
            now = 1_700_000_000_000L
            library.saveItem("b-late", "Summary B", "Beta body.", "Notebook", KnowledgeProvenance.OWNER_SUPPLIED)
            now = 1_700_000_000_500L
            library.saveItem("a-early", "Summary A", "Alpha body.", "Notebook", KnowledgeProvenance.OWNER_SUPPLIED)
            now = 1_700_000_001_000L
            library.saveItem("c-other", "Other", "Gamma body.", "Scratchpad", KnowledgeProvenance.OWNER_OBSERVED)

            val all = library.search("")
            assertEquals(listOf("b-late", "a-early", "c-other"), all.items.map { it.id })
            assertEquals(3, all.totalMatches)
            assertFalse(all.truncated)

            val summary = library.search("SUMMARY")
            assertEquals(listOf("b-late", "a-early"), summary.items.map { it.id })
            assertEquals(2, summary.totalMatches)

            val sourceHit = library.search("notebook")
            assertEquals(2, sourceHit.totalMatches)

            assertEquals(0, library.search("nothing matches this").totalMatches)
        }
    }

    @Test
    fun searchCapIsBoundedAndTruncationIsReported() {
        withLibrary { library, _, _ ->
            repeat(5) { index ->
                now = 1_700_000_000_000L + index
                library.saveItem(
                    "item-$index", "Summary $index", "Body $index.", "Notebook", KnowledgeProvenance.OWNER_SUPPLIED,
                )
            }

            val capped = library.search("summary", limit = 2)
            assertEquals(2, capped.items.size)
            assertEquals(5, capped.totalMatches)
            assertTrue("truncation must be visible, not silent", capped.truncated)

            val uncapped = library.search("summary", limit = 5)
            assertEquals(5, uncapped.items.size)
            assertFalse(uncapped.truncated)
        }
    }

    @Test
    fun searchRejectsOutOfRangeQueryAndLimit() {
        withLibrary { library, _, _ ->
            assertThrows(IllegalArgumentException::class.java) {
                library.search("a".repeat(KnowledgeSearchBounds.MAX_QUERY_LENGTH + 1))
            }
            listOf(0, -1, KnowledgeSearchBounds.MAX_LIMIT + 1).forEach { badLimit ->
                assertThrows(IllegalArgumentException::class.java) { library.search("", limit = badLimit) }
            }
            // The maximum limit is accepted; the count reflects stored items, not the cap.
            val atMax = library.search("", limit = KnowledgeSearchBounds.MAX_LIMIT)
            assertEquals(0, atMax.items.size)
            assertEquals(0, atMax.totalMatches)
            assertFalse(atMax.truncated)
        }
    }

    @Test
    fun legacyVersionOneFileIsReadAndUpgradedToVersionTwo() {
        val databaseFile = File(temporaryFolder.newFolder(), "legacy.db")
        databaseFile.writeText(legacyVersionOneFile(), Charsets.UTF_8)
        assertEquals("4CBON2_LOCAL_ENTITY_DB\t1", databaseFile.readLines(Charsets.UTF_8).first())

        val database = LocalEntityDatabase(databaseFile)
        database.open()
        try {
            // Version 1 rows survive untouched.
            val draft = database.findDraft("draft-legacy")!!
            assertEquals("Legacy draft", draft.title)
            assertEquals("Legacy goal.", draft.goal)
            assertEquals(LocalDraftState.DRAFT, draft.state)
            assertEquals(listOf("event-legacy"), database.events().map { it.id })

            // The new entity type is available and empty, not corrupt.
            assertEquals(emptyList<LocalKnowledgeItem>(), database.knowledgeItems())

            // The header was upgraded in place.
            assertEquals("4CBON2_LOCAL_ENTITY_DB\t2", databaseFile.readLines(Charsets.UTF_8).first())

            // And a version 2 write still coexists with the legacy rows.
            database.saveKnowledgeItem(
                LocalKnowledgeItem(
                    id = "report-1",
                    title = "After migration",
                    body = "New body.",
                    sourceLabel = "Notebook",
                    provenance = KnowledgeProvenance.OWNER_SUPPLIED,
                    savedAtEpochMillis = 1_700_000_000_000L,
                ),
            )
            assertEquals(1, database.knowledgeItems().size)
        } finally {
            database.close()
        }

        val reopened = LocalEntityDatabase(databaseFile)
        reopened.open()
        try {
            assertEquals("Legacy draft", reopened.findDraft("draft-legacy")!!.title)
            assertEquals(1, reopened.knowledgeItems().size)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun saveFromDraftRecordsDraftDerivedProvenance() {
        withLibrary { library, database, _ ->
            assertEquals(null, database.findDraft("missing"))

            val draft = LocalAgentDraft(
                id = "draft-source",
                title = "Draft title",
                goal = "Draft goal text.",
                templateName = "template",
                state = LocalDraftState.DRAFT,
                createdAtEpochMillis = 1_699_000_000_000L,
                updatedAtEpochMillis = 1_699_000_000_000L,
            )
            database.saveDraft(draft)

            val saved = library.saveFromDraft(
                draftId = "draft-source",
                itemId = "derived-1",
                sourceLabel = "Draft export",
            ) as OfflineKnowledgeLibrary.SaveResult.Saved

            assertEquals("Draft title", saved.item.title)
            assertEquals("Draft goal text.", saved.item.body)
            assertEquals(KnowledgeProvenance.LOCAL_DRAFT_DERIVED, saved.item.provenance)

            assertEquals(
                OfflineKnowledgeLibrary.SaveResult.Refused("derived-2", KnowledgeRefusal.UNKNOWN_ITEM),
                library.saveFromDraft("no-such-draft", "derived-2", "Draft export"),
            )
        }
    }

    @Test
    fun libraryStaysInertWithRespectToRegistryScopeAndLease() {
        withLibrary { library, _, _ ->
            val registry = SelectedAppCapabilityRegistry(ActionRouteProbe.NONE)
            val lease = ActionLease(createdAtEpochMillis = now, nowEpochMillis = { now })
            lease.apply(
                LeaseCommand(LeaseCommandKind.STOP, LeaseCommandOrigin.OWNER_LOCAL, epoch = 1, issuedAtEpochMillis = now),
            )

            library.saveItem("inert-1", "Title", "Body.", "Notebook", KnowledgeProvenance.OWNER_SUPPLIED)

            assertFalse(registry.isSelected("org.kaggle.app"))
            assertEquals(
                CapabilityDecision.Unavailable("org.kaggle.app", "open", UnavailableReason.APP_NOT_SELECTED),
                registry.resolve("org.kaggle.app", "open"),
            )
            assertEquals(LeaseState.STOPPED, lease.state)
            assertEquals(LeaseRefusal.LEASE_TERMINATED, lease.stepRefusal(now))
        }
    }

    @Test
    fun knowledgeRowsAreSerialisedAsTheirOwnEntityType() {
        withLibrary { library, _, databaseFile ->
            library.saveItem("row-1", "Row title", "Row body.", "Row source", KnowledgeProvenance.OWNER_OBSERVED)

            val lines = databaseFile.readLines(Charsets.UTF_8)
            assertEquals("4CBON2_LOCAL_ENTITY_DB\t2", lines.first())
            val knowledgeLines = lines.drop(1).filter { it.startsWith("KNOWLEDGE\t") }
            assertEquals(1, knowledgeLines.size)
            assertEquals(7, knowledgeLines.single().split('\t').size)
            assertTrue(knowledgeLines.single().split('\t')[5] == "OWNER_OBSERVED")
        }
    }

    // --- helpers -------------------------------------------------------------------

    private fun withLibrary(block: (OfflineKnowledgeLibrary, LocalEntityDatabase, File) -> Unit) {
        val databaseFile = File(temporaryFolder.newFolder(), "knowledge.db")
        val database = LocalEntityDatabase(databaseFile)
        database.open()
        try {
            block(OfflineKnowledgeLibrary(database, { now }), database, databaseFile)
        } finally {
            database.close()
        }
    }

    private fun legacyVersionOneFile(): String {
        val encode = { value: String ->
            Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
        }
        return listOf(
            "4CBON2_LOCAL_ENTITY_DB\t1",
            listOf(
                "DRAFT",
                encode("draft-legacy"),
                encode("Legacy draft"),
                encode("Legacy goal."),
                encode("legacy-template"),
                "DRAFT",
                "1699000000000",
                "1699000000000",
            ).joinToString("\t"),
            listOf(
                "EVENT",
                encode("event-legacy"),
                encode("draft-legacy"),
                "DRAFT_CREATED",
                "PLANNED",
                encode("created"),
                "1699000000000",
            ).joinToString("\t"),
        ).joinToString("\n", postfix = "\n")
    }
}
