package io.github.mohamtur1.fourcbon2

import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalEntityDatabaseTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun recordsSurviveCloseAndIndependentReopenFromFileBackedStorage() {
        val databaseFile = databaseFile()
        val draft = syntheticDraft()
        val event = syntheticEvent(draftId = draft.id)
        val firstInstance = LocalEntityDatabase(databaseFile)

        firstInstance.open()
        firstInstance.saveDraft(draft)
        firstInstance.recordEvent(event)
        firstInstance.close()

        assertTrue(databaseFile.isFile)
        assertTrue(databaseFile.length() > 0L)
        assertFalse(databaseFile.readText(Charsets.UTF_8).contains("Synthetic local draft"))

        val reopenedInstance = LocalEntityDatabase(databaseFile)
        reopenedInstance.open()
        try {
            assertEquals(listOf(draft), reopenedInstance.drafts())
            assertEquals(listOf(event), reopenedInstance.events())
            assertEquals(draft, reopenedInstance.findDraft(draft.id))
        } finally {
            reopenedInstance.close()
        }
    }

    @Test
    fun freshDatabaseWritesTheCurrentSchemaHeader() {
        val databaseFile = databaseFile()
        val database = LocalEntityDatabase(databaseFile)

        database.open()
        database.close()

        val lines = databaseFile.readLines(Charsets.UTF_8)
        // Division 19 raised the current schema to version 3 and kept versions 1 and 2 readable.
        assertEquals("4CBON2_LOCAL_ENTITY_DB\t3", lines.first())
        assertEquals(3, LocalEntityDatabase.SCHEMA_VERSION)
        assertEquals(1, LocalEntityDatabase.OLDEST_READABLE_SCHEMA_VERSION)
        assertEquals(emptyList<String>(), lines.drop(1))
    }

    @Test
    fun unsupportedSchemaAndMalformedRowsAreRejected() {
        val databaseFile = databaseFile()

        // Version 3 is now current, so an unknown future version must be the rejected one.
        databaseFile.writeText("4CBON2_LOCAL_ENTITY_DB\t4\n", Charsets.UTF_8)
        assertThrows(IllegalStateException::class.java) {
            LocalEntityDatabase(databaseFile).open()
        }

        // A readable legacy version 1 header still rejects a malformed row.
        databaseFile.writeText("4CBON2_LOCAL_ENTITY_DB\t1\nDRAFT\tbad\n", Charsets.UTF_8)
        assertThrows(IllegalStateException::class.java) {
            LocalEntityDatabase(databaseFile).open()
        }
    }

    @Test
    fun legacyVersionTwoFileLoadsUnchangedAndUpgradesToVersionThree() {
        val databaseFile = databaseFile()
        // A version 2 file as Division 17 wrote it: header 2 plus one knowledge row.
        val knowledgeId = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("kept-1".toByteArray(Charsets.UTF_8))
        val title = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("Synthetic title".toByteArray(Charsets.UTF_8))
        val body = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("Synthetic body.".toByteArray(Charsets.UTF_8))
        val label = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("Notebook".toByteArray(Charsets.UTF_8))
        databaseFile.writeText(
            "4CBON2_LOCAL_ENTITY_DB\t2\n" +
                "KNOWLEDGE\t$knowledgeId\t$title\t$body\t$label\tOWNER_SUPPLIED\t1700000000000\n",
            Charsets.UTF_8,
        )

        val database = LocalEntityDatabase(databaseFile)
        database.open()
        try {
            // Rows survive the header upgrade untouched.
            assertEquals("Synthetic title", database.knowledgeItems().single().title)
            assertEquals(emptyList<LocalQueuedJob>(), database.queuedJobs())
            // Opening normalises the header to the current version.
            assertEquals("4CBON2_LOCAL_ENTITY_DB\t3", databaseFile.readLines(Charsets.UTF_8).first())
        } finally {
            database.close()
        }

        // A fresh instance reads the upgraded file and still sees the row.
        val reopened = LocalEntityDatabase(databaseFile)
        reopened.open()
        try {
            assertEquals("Synthetic body.", reopened.findKnowledgeItem("kept-1")?.body)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun closedDatabaseAndInvalidSyntheticEntitiesAreRejected() {
        val database = LocalEntityDatabase(databaseFile())
        val draft = syntheticDraft()
        val event = syntheticEvent(draftId = draft.id)

        assertThrows(IllegalStateException::class.java) { database.drafts() }
        database.open()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                database.saveDraft(draft.copy(id = "not a valid id"))
            }
            assertThrows(IllegalArgumentException::class.java) {
                database.recordEvent(event.copy(draftId = "missing-draft"))
            }
        } finally {
            database.close()
        }
        assertThrows(IllegalStateException::class.java) { database.saveDraft(draft) }
    }

    private fun databaseFile(): File = File(
        temporaryFolder.root,
        LocalEntityDatabase.DEFAULT_FILE_NAME,
    )

    private fun syntheticDraft(): LocalAgentDraft = LocalAgentDraft(
        id = "synthetic-draft-001",
        title = "Synthetic local draft",
        goal = "Prove that a pretend draft survives a close and reopen.",
        templateName = "local-check",
        state = LocalDraftState.WAITING_FOR_REVIEW,
        createdAtEpochMillis = 1_700_000_000_000L,
        updatedAtEpochMillis = 1_700_000_000_500L,
    )

    private fun syntheticEvent(draftId: String): LocalActionEvent = LocalActionEvent(
        id = "synthetic-event-001",
        draftId = draftId,
        kind = LocalActionKind.LOCAL_CHECK_RECORDED,
        outcome = LocalActionOutcome.COMPLETED,
        note = "Synthetic file-backed reopen check",
        createdAtEpochMillis = 1_700_000_001_000L,
    )
}
