package io.github.mohamtur1.fourcbon2

import java.util.UUID

/** Why a draft operation was refused. Every refusal names exactly one cause. */
enum class DraftRefusal {
    UNKNOWN_DRAFT,
    DUPLICATE_DRAFT_ID,
    EDIT_NOT_ALLOWED_IN_STATE,
    INVALID_STATE_TRANSITION,
}

/**
 * The result of one draft operation: either a stored draft with the event that was
 * recorded alongside it, or an explicit refusal. There is no silent partial write.
 */
sealed interface DraftOutcome {
    data class Saved(
        val draft: LocalAgentDraft,
        val event: LocalActionEvent,
    ) : DraftOutcome

    data class Refused(
        val draftId: String,
        val reason: DraftRefusal,
    ) : DraftOutcome
}

/**
 * Bounded, local create/edit/list/load access to offline agent drafts.
 *
 * Drafts are **inert**. This repository has no reference to the capability registry,
 * the scope contract, or the control lease, and saving a draft never grants a
 * permission, starts a job, or bypasses any of them. A draft is only text the owner can
 * return to later.
 *
 * Storage is the existing `LocalEntityDatabase` at its current schema version, so this
 * division adds no schema migration, no new file format, no new dependency, and no
 * encrypted-storage claim. Every write is paired with a fixed-kind `LocalActionEvent`
 * so the local trail matches what the owner sees.
 */
class OfflineDraftRepository(
    private val database: LocalEntityDatabase,
    private val nowEpochMillis: () -> Long,
    private val identity: () -> String = { UUID.randomUUID().toString() },
) {
    companion object {
        /** Largest accepted goal length, matching [LocalAgentDraft]. */
        const val MAX_GOAL_LENGTH = 1_000

        /** Largest accepted title length, matching [LocalAgentDraft]. */
        const val MAX_TITLE_LENGTH = 120

        /** Largest accepted template-name length, matching [LocalAgentDraft]. */
        const val MAX_TEMPLATE_LENGTH = 80

        /** States in which an owner may still edit the draft text. */
        private val EDITABLE_STATES = setOf(LocalDraftState.DRAFT, LocalDraftState.WAITING_FOR_REVIEW)
    }

    /**
     * Creates a local draft in [LocalDraftState.DRAFT] and records a `DRAFT_CREATED`
     * event. A caller may supply the identifier for deterministic use; otherwise a
     * random identifier is used.
     */
    fun createDraft(
        title: String,
        goal: String,
        templateName: String,
        id: String = identity(),
    ): DraftOutcome {
        if (database.findDraft(id) != null) {
            return DraftOutcome.Refused(draftId = id, reason = DraftRefusal.DUPLICATE_DRAFT_ID)
        }

        val createdAt = nowEpochMillis()
        val draft = LocalAgentDraft(
            id = id,
            title = title,
            goal = goal,
            templateName = templateName,
            state = LocalDraftState.DRAFT,
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = createdAt,
        )
        return save(draft, kind = LocalActionKind.DRAFT_CREATED, outcome = LocalActionOutcome.PLANNED)
    }

    /**
     * Rewrites the owner-editable text of a draft without changing its identity, state,
     * or creation time. Refused for unknown drafts and for cancelled drafts.
     */
    fun editDraft(
        id: String,
        title: String,
        goal: String,
        templateName: String,
    ): DraftOutcome {
        val existing = database.findDraft(id)
            ?: return DraftOutcome.Refused(draftId = id, reason = DraftRefusal.UNKNOWN_DRAFT)
        if (!EDITABLE_STATES.contains(existing.state)) {
            return DraftOutcome.Refused(draftId = id, reason = DraftRefusal.EDIT_NOT_ALLOWED_IN_STATE)
        }

        val edited = LocalAgentDraft(
            id = existing.id,
            title = title,
            goal = goal,
            templateName = templateName,
            state = existing.state,
            createdAtEpochMillis = existing.createdAtEpochMillis,
            updatedAtEpochMillis = maxOf(nowEpochMillis(), existing.createdAtEpochMillis),
        )
        return save(edited, kind = LocalActionKind.DRAFT_UPDATED, outcome = LocalActionOutcome.PLANNED)
    }

    /** Moves a `DRAFT` to `WAITING_FOR_REVIEW`, recording a local check event. */
    fun submitForReview(id: String): DraftOutcome = transition(
        id = id,
        target = LocalDraftState.WAITING_FOR_REVIEW,
        allowedFrom = setOf(LocalDraftState.DRAFT),
        kind = LocalActionKind.LOCAL_CHECK_RECORDED,
        outcome = LocalActionOutcome.PLANNED,
    )

    /** Returns a `WAITING_FOR_REVIEW` draft to `DRAFT` so the owner can keep editing. */
    fun returnToDraft(id: String): DraftOutcome = transition(
        id = id,
        target = LocalDraftState.DRAFT,
        allowedFrom = setOf(LocalDraftState.WAITING_FOR_REVIEW),
        kind = LocalActionKind.DRAFT_UPDATED,
        outcome = LocalActionOutcome.PLANNED,
    )

    /** Cancels a draft. `CANCELLED` is terminal: later edits and transitions are refused. */
    fun cancelDraft(id: String): DraftOutcome = transition(
        id = id,
        target = LocalDraftState.CANCELLED,
        allowedFrom = setOf(LocalDraftState.DRAFT, LocalDraftState.WAITING_FOR_REVIEW),
        kind = LocalActionKind.DRAFT_UPDATED,
        outcome = LocalActionOutcome.CANCELLED,
    )

    /**
     * Records that a manual handoff was prepared for the owner. The draft text and state
     * are unchanged: this only notes that the owner was handed the next step, and it
     * never performs that step.
     */
    fun prepareManualHandoff(id: String): DraftOutcome {
        val existing = database.findDraft(id)
            ?: return DraftOutcome.Refused(draftId = id, reason = DraftRefusal.UNKNOWN_DRAFT)
        if (existing.state == LocalDraftState.CANCELLED) {
            return DraftOutcome.Refused(draftId = id, reason = DraftRefusal.EDIT_NOT_ALLOWED_IN_STATE)
        }
        return recordEvent(existing, kind = LocalActionKind.MANUAL_HANDOFF_PREPARED, outcome = LocalActionOutcome.PLANNED)
    }

    fun loadDraft(id: String): LocalAgentDraft? = database.findDraft(id)

    /** All drafts, oldest first, with the identifier as a stable tie-break. */
    fun listDrafts(): List<LocalAgentDraft> = database.drafts()

    /** Events recorded for one draft, oldest first. Empty for an unknown draft. */
    fun eventsFor(id: String): List<LocalActionEvent> =
        database.events().filter { event -> event.draftId == id }

    private fun transition(
        id: String,
        target: LocalDraftState,
        allowedFrom: Set<LocalDraftState>,
        kind: LocalActionKind,
        outcome: LocalActionOutcome,
    ): DraftOutcome {
        val existing = database.findDraft(id)
            ?: return DraftOutcome.Refused(draftId = id, reason = DraftRefusal.UNKNOWN_DRAFT)
        if (!allowedFrom.contains(existing.state)) {
            return DraftOutcome.Refused(draftId = id, reason = DraftRefusal.INVALID_STATE_TRANSITION)
        }

        val moved = LocalAgentDraft(
            id = existing.id,
            title = existing.title,
            goal = existing.goal,
            templateName = existing.templateName,
            state = target,
            createdAtEpochMillis = existing.createdAtEpochMillis,
            updatedAtEpochMillis = maxOf(nowEpochMillis(), existing.createdAtEpochMillis),
        )
        return save(moved, kind = kind, outcome = outcome)
    }

    private fun save(
        draft: LocalAgentDraft,
        kind: LocalActionKind,
        outcome: LocalActionOutcome,
    ): DraftOutcome {
        val event = nextEvent(draft, kind = kind, outcome = outcome)
        database.saveDraft(draft)
        database.recordEvent(event)
        return DraftOutcome.Saved(draft = draft, event = event)
    }

    private fun recordEvent(
        draft: LocalAgentDraft,
        kind: LocalActionKind,
        outcome: LocalActionOutcome,
    ): DraftOutcome {
        val event = nextEvent(draft, kind = kind, outcome = outcome)
        database.recordEvent(event)
        return DraftOutcome.Saved(draft = draft, event = event)
    }

    /**
     * Builds a short, stable event identifier that stays within the identifier limit even
     * for a 64-character draft identifier: a hash suffix plus the per-draft event count.
     * The count is recomputed from the file, so identifiers stay unique after reopen.
     */
    private fun nextEvent(
        draft: LocalAgentDraft,
        kind: LocalActionKind,
        outcome: LocalActionOutcome,
    ): LocalActionEvent {
        val existingForDraft = eventsFor(draft.id).size
        return LocalActionEvent(
            id = "evt-${Integer.toHexString(draft.id.hashCode())}-${existingForDraft + 1}",
            draftId = draft.id,
            kind = kind,
            outcome = outcome,
            note = "draft ${draft.state.name.lowercase()}",
            createdAtEpochMillis = maxOf(nowEpochMillis(), draft.createdAtEpochMillis),
        )
    }
}
