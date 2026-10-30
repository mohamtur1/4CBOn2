package io.github.mohamtur1.fourcbon2

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineDraftRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private var now = 1_700_000_000_000L

    @Test
    fun createDraftSavesTheDraftAndRecordsItsCreationEvent() {
        withRepository { repository ->
            val outcome = repository.createDraft(
                title = "Synthetic draft",
                goal = "Summarise one synthetic report.",
                templateName = "summary-template",
                id = "draft-1",
            )

            val saved = outcome as DraftOutcome.Saved
            assertEquals(LocalDraftState.DRAFT, saved.draft.state)
            assertEquals("draft-1", saved.draft.id)
            assertEquals(saved.draft.createdAtEpochMillis, saved.draft.updatedAtEpochMillis)
            assertEquals(LocalActionKind.DRAFT_CREATED, saved.event.kind)
            assertEquals(LocalActionOutcome.PLANNED, saved.event.outcome)
            assertEquals("draft-1", saved.event.draftId)

            assertEquals(saved.draft, repository.loadDraft("draft-1"))
            assertEquals(listOf(saved.draft), repository.listDrafts())
            assertEquals(listOf(saved.event), repository.eventsFor("draft-1"))
        }
    }

    @Test
    fun createdDraftSurvivesCloseAndIndependentReopen() {
        val databaseFile = File(temporaryFolder.newFolder(), "drafts.db")
        val firstDatabase = LocalEntityDatabase(databaseFile)
        firstDatabase.open()
        val firstRepository = OfflineDraftRepository(firstDatabase, { now }, { "draft-persisted" })
        val created = firstRepository.createDraft(
            title = "Synthetic persisted draft",
            goal = "Goal text held only in a local file.",
            templateName = "offline-template",
        ) as DraftOutcome.Saved
        firstRepository.editDraft(
            id = created.draft.id,
            title = "Synthetic persisted draft",
            goal = "Edited goal text held only in a local file.",
            templateName = "offline-template",
        )
        firstDatabase.close()

        assertTrue(databaseFile.isFile)
        assertFalse(databaseFile.readText(Charsets.UTF_8).contains("Edited goal text"))

        val reopenedDatabase = LocalEntityDatabase(databaseFile)
        reopenedDatabase.open()
        try {
            val reopenedRepository = OfflineDraftRepository(reopenedDatabase, { now }, { "unused-id" })
            val reloaded = reopenedRepository.loadDraft("draft-persisted")!!
            assertEquals("Edited goal text held only in a local file.", reloaded.goal)
            assertEquals(LocalDraftState.DRAFT, reloaded.state)
            assertEquals(listOf("draft-persisted"), reopenedRepository.listDrafts().map { it.id })
            assertEquals(
                listOf(LocalActionKind.DRAFT_CREATED, LocalActionKind.DRAFT_UPDATED),
                reopenedRepository.eventsFor("draft-persisted").map { it.kind },
            )
        } finally {
            reopenedDatabase.close()
        }
    }

    @Test
    fun createDraftRefusesADuplicateIdentifierWithoutOverwriting() {
        withRepository { repository ->
            repository.createDraft("First", "First goal.", "template-a", id = "draft-dup")

            val refused = repository.createDraft("Second", "Second goal.", "template-b", id = "draft-dup")
                as DraftOutcome.Refused

            assertEquals(DraftRefusal.DUPLICATE_DRAFT_ID, refused.reason)
            assertEquals("First", repository.loadDraft("draft-dup")!!.title)
            assertEquals(1, repository.eventsFor("draft-dup").size)
        }
    }

    @Test
    fun createDraftValidatesBoundedFieldsBeforeWritingAnything() {
        withRepository { repository ->
            assertThrows(IllegalArgumentException::class.java) {
                repository.createDraft("   ", "Goal.", "template", id = "draft-blank")
            }
            assertThrows(IllegalArgumentException::class.java) {
                repository.createDraft("a".repeat(OfflineDraftRepository.MAX_TITLE_LENGTH + 1), "Goal.", "t", "draft-long")
            }
            assertThrows(IllegalArgumentException::class.java) {
                repository.createDraft("Title", "a".repeat(OfflineDraftRepository.MAX_GOAL_LENGTH + 1), "t", "draft-goal")
            }
            assertThrows(IllegalArgumentException::class.java) {
                repository.createDraft("Title", "Goal.", "a".repeat(OfflineDraftRepository.MAX_TEMPLATE_LENGTH + 1), "draft-tpl")
            }
            assertThrows(IllegalArgumentException::class.java) {
                repository.createDraft("Title", "Goal.", "bad\ttemplate", "draft-ctrl")
            }

            assertTrue(repository.listDrafts().isEmpty())
        }
    }

    @Test
    fun editDraftRewritesTextAndAdvancesOnlyTheUpdateTime() {
        withRepository { repository ->
            now = 1_700_000_000_000L
            val created = repository.createDraft("Old title", "Old goal.", "template-a", "draft-edit")
                as DraftOutcome.Saved

            now = 1_700_000_050_000L
            val edited = repository.editDraft(
                id = "draft-edit",
                title = "New title",
                goal = "New goal.",
                templateName = "template-b",
            ) as DraftOutcome.Saved

            assertEquals("New title", edited.draft.title)
            assertEquals("New goal.", edited.draft.goal)
            assertEquals("template-b", edited.draft.templateName)
            assertEquals(created.draft.createdAtEpochMillis, edited.draft.createdAtEpochMillis)
            assertEquals(1_700_000_050_000L, edited.draft.updatedAtEpochMillis)
            assertEquals(LocalActionKind.DRAFT_UPDATED, edited.event.kind)
            assertEquals("New goal.", repository.loadDraft("draft-edit")!!.goal)
        }
    }

    @Test
    fun editDraftRefusesAnUnknownDraft() {
        withRepository { repository ->
            val refused = repository.editDraft("missing", "Title", "Goal.", "template") as DraftOutcome.Refused

            assertEquals(DraftRefusal.UNKNOWN_DRAFT, refused.reason)
            assertNull(repository.loadDraft("missing"))
            assertTrue(repository.eventsFor("missing").isEmpty())
        }
    }

    @Test
    fun editDraftPreservesIdentityStateAndCreationTime() {
        withRepository { repository ->
            val created = repository.createDraft("Title", "Goal.", "template", "draft-identity")
                as DraftOutcome.Saved
            repository.submitForReview("draft-identity")

            val edited = repository.editDraft("draft-identity", "Title 2", "Goal 2.", "template")
                as DraftOutcome.Saved

            assertEquals(created.draft.createdAtEpochMillis, edited.draft.createdAtEpochMillis)
            assertEquals("draft-identity", edited.draft.id)
            assertEquals(LocalDraftState.WAITING_FOR_REVIEW, edited.draft.state)
        }
    }

    @Test
    fun listDraftsIsOrderedByCreationTimeThenIdentifier() {
        withRepository { repository ->
            now = 1_700_000_000_000L
            repository.createDraft("Late", "Goal.", "t", "draft-b")
            now = 1_700_000_000_500L
            repository.createDraft("Early", "Goal.", "t", "draft-a")

            assertEquals(
                listOf("draft-b", "draft-a"),
                repository.listDrafts().map { it.id },
            )
        }
    }

    @Test
    fun submitForReviewMovesTheDraftAndRecordsALocalCheck() {
        withRepository { repository ->
            repository.createDraft("Title", "Goal.", "template", "draft-submit")

            val submitted = repository.submitForReview("draft-submit") as DraftOutcome.Saved

            assertEquals(LocalDraftState.WAITING_FOR_REVIEW, submitted.draft.state)
            assertEquals(LocalActionKind.LOCAL_CHECK_RECORDED, submitted.event.kind)
            assertEquals(LocalDraftState.WAITING_FOR_REVIEW, repository.loadDraft("draft-submit")!!.state)
        }
    }

    @Test
    fun submitForReviewRefusesAnInvalidStartingStateWithoutWriting() {
        withRepository { repository ->
            repository.createDraft("Title", "Goal.", "template", "draft-twice")
            repository.submitForReview("draft-twice")
            val eventsAfterFirstSubmit = repository.eventsFor("draft-twice").size

            val refused = repository.submitForReview("draft-twice") as DraftOutcome.Refused

            assertEquals(DraftRefusal.INVALID_STATE_TRANSITION, refused.reason)
            assertEquals(eventsAfterFirstSubmit, repository.eventsFor("draft-twice").size)
            assertEquals(DraftRefusal.UNKNOWN_DRAFT, (repository.submitForReview("nope") as DraftOutcome.Refused).reason)
        }
    }

    @Test
    fun cancelMakesTheDraftTerminalForEditingAndTransition() {
        withRepository { repository ->
            repository.createDraft("Title", "Goal.", "template", "draft-cancel")

            val cancelled = repository.cancelDraft("draft-cancel") as DraftOutcome.Saved
            assertEquals(LocalDraftState.CANCELLED, cancelled.draft.state)
            assertEquals(LocalActionOutcome.CANCELLED, cancelled.event.outcome)

            assertEquals(
                DraftRefusal.EDIT_NOT_ALLOWED_IN_STATE,
                (repository.editDraft("draft-cancel", "T", "G", "t") as DraftOutcome.Refused).reason,
            )
            assertEquals(
                DraftRefusal.INVALID_STATE_TRANSITION,
                (repository.submitForReview("draft-cancel") as DraftOutcome.Refused).reason,
            )
            assertEquals(
                DraftRefusal.INVALID_STATE_TRANSITION,
                (repository.returnToDraft("draft-cancel") as DraftOutcome.Refused).reason,
            )
            assertEquals(
                DraftRefusal.EDIT_NOT_ALLOWED_IN_STATE,
                (repository.prepareManualHandoff("draft-cancel") as DraftOutcome.Refused).reason,
            )
            assertEquals(LocalDraftState.CANCELLED, repository.loadDraft("draft-cancel")!!.state)
        }
    }

    @Test
    fun cancelAndReturnToDraftWorkFromWaitingForReview() {
        withRepository { repository ->
            repository.createDraft("Title", "Goal.", "template", "draft-cycle")
            repository.submitForReview("draft-cycle")

            val returned = repository.returnToDraft("draft-cycle") as DraftOutcome.Saved
            assertEquals(LocalDraftState.DRAFT, returned.draft.state)

            repository.submitForReview("draft-cycle")
            val cancelled = repository.cancelDraft("draft-cycle") as DraftOutcome.Saved
            assertEquals(LocalDraftState.CANCELLED, cancelled.draft.state)
        }
    }

    @Test
    fun prepareManualHandoffRecordsAnEventWithoutChangingTheDraft() {
        withRepository { repository ->
            val created = repository.createDraft("Title", "Goal.", "template", "draft-handoff")
                as DraftOutcome.Saved

            val prepared = repository.prepareManualHandoff("draft-handoff") as DraftOutcome.Saved

            assertEquals(LocalActionKind.MANUAL_HANDOFF_PREPARED, prepared.event.kind)
            assertEquals(created.draft, prepared.draft)
            assertEquals(created.draft, repository.loadDraft("draft-handoff"))
            assertEquals(
                DraftRefusal.UNKNOWN_DRAFT,
                (repository.prepareManualHandoff("missing") as DraftOutcome.Refused).reason,
            )
        }
    }

    @Test
    fun draftsStayInertWithRespectToTheRegistryLeaseAndScope() {
        withRepository { repository ->
            val registry = SelectedAppCapabilityRegistry(ActionRouteProbe.NONE)
            val lease = ActionLease(createdAtEpochMillis = now, nowEpochMillis = { now })
            lease.apply(
                LeaseCommand(
                    kind = LeaseCommandKind.STOP,
                    origin = LeaseCommandOrigin.OWNER_LOCAL,
                    epoch = 1,
                    issuedAtEpochMillis = now,
                ),
            )

            // Saving drafts works even though no app is selected and the lease is stopped.
            assertTrue(repository.createDraft("Title", "Goal.", "template", "draft-inert") is DraftOutcome.Saved)

            // And drafting neither selects apps nor revives the lease.
            assertFalse(registry.isSelected("org.kaggle.app"))
            assertEquals(
                CapabilityDecision.Unavailable(
                    "org.kaggle.app",
                    "open",
                    UnavailableReason.APP_NOT_SELECTED,
                ),
                registry.resolve("org.kaggle.app", "open"),
            )
            assertEquals(LeaseState.STOPPED, lease.state)
            assertEquals(LeaseRefusal.LEASE_TERMINATED, lease.stepRefusal(now))
        }
    }

    @Test
    fun eventIdentifiersStayUniqueAcrossReopenAndFitTheIdentifierLimit() {
        val databaseFile = File(temporaryFolder.newFolder(), "events.db")
        val database = LocalEntityDatabase(databaseFile)
        database.open()
        val repository = OfflineDraftRepository(database, { now }, { "draft-" + "x".repeat(58) })
        val draftId = "draft-" + "x".repeat(58)
        repository.createDraft("Title", "Goal.", "template")
        repository.editDraft(draftId, "Title 2", "Goal 2.", "template")

        // Simulate a restart: the same file is closed and reopened under a new repository.
        database.close()
        database.open()
        val afterReopen = OfflineDraftRepository(database, { now }, { "unused" })
        afterReopen.editDraft(draftId, "Title 3", "Goal 3.", "template")
        database.close()

        val reopened = LocalEntityDatabase(databaseFile)
        reopened.open()
        try {
            val events = reopened.events().filter { it.draftId == draftId }
            assertEquals(3, events.size)
            assertEquals(3, events.map { it.id }.toSet().size)
            assertTrue(events.all { it.id.length <= 64 })
            assertTrue(events.all { Regex("[A-Za-z0-9._:-]{1,64}").matches(it.id) })
        } finally {
            reopened.close()
        }
    }

    private fun withRepository(block: (OfflineDraftRepository) -> Unit) {
        val database = LocalEntityDatabase(File(temporaryFolder.newFolder(), "drafts.db"))
        database.open()
        try {
            block(OfflineDraftRepository(database, { now }, { "draft-generated" }))
        } finally {
            database.close()
        }
    }

}
