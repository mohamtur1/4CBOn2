package io.github.mohamtur1.fourcbon2.agents

import io.github.mohamtur1.fourcbon2.local.DraftState
import io.github.mohamtur1.fourcbon2.local.GoalDraft

/**
 * Offline agent drafts (Division 15).
 *
 * Goals, templates, job profiles, caps, and draft state are saved locally
 * and editable without network. Drafts are bounded: an instantiated draft
 * cannot exceed its recorded budget or permission scope.
 */
class AgentDraftEngine(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val drafts = mutableListOf<GoalDraft>()

    fun create(title: String, goalText: String, templateId: String, budget: Int): GoalDraft {
        require(title.isNotBlank()) { "Title is required." }
        require(goalText.isNotBlank()) { "Goal text is required." }
        require(templateId.isNotBlank()) { "Template id is required." }
        require(budget > 0) { "Budget must be positive." }
        val draft = GoalDraft(
            id = "draft-${drafts.size + 1}",
            title = title,
            goalText = goalText,
            templateId = templateId,
            budget = budget,
            permissionScope = DEFAULT_SCOPE,
            state = DraftState.DRAFT,
        )
        drafts.add(draft)
        return draft
    }

    fun load(id: String): GoalDraft? = drafts.find { it.id == id }

    fun update(id: String, title: String, goalText: String): GoalDraft? {
        val draft = load(id) ?: return null
        require(draft.state != DraftState.QUEUED) { "Queued drafts cannot be edited." }
        val updated = draft.copy(title = title, goalText = goalText)
        drafts.remove(draft)
        drafts.add(updated)
        return updated
    }

    /** Instantiate a template into a ready-to-queue draft. */
    fun instantiateTemplate(id: String): GoalDraft? {
        val draft = load(id) ?: return null
        require(draft.state == DraftState.DRAFT) { "Only drafts can be instantiated." }
        val updated = draft.copy(state = DraftState.TEMPLATE_INSTANTIATED)
        drafts.remove(draft)
        drafts.add(updated)
        return updated
    }

    fun markReadyToQueue(id: String): GoalDraft? {
        val draft = load(id) ?: return null
        require(draft.state == DraftState.TEMPLATE_INSTANTIATED) {
            "Only instantiated drafts can be marked ready."
        }
        val updated = draft.copy(state = DraftState.READY_TO_QUEUE)
        drafts.remove(draft)
        drafts.add(updated)
        return updated
    }

    fun markQueued(id: String): GoalDraft? {
        val draft = load(id) ?: return null
        require(draft.state == DraftState.READY_TO_QUEUE) { "Only ready drafts can be queued." }
        val updated = draft.copy(state = DraftState.QUEUED)
        drafts.remove(draft)
        drafts.add(updated)
        return updated
    }

    fun cancel(id: String): GoalDraft? {
        val draft = load(id) ?: return null
        require(draft.state != DraftState.QUEUED) { "Queued drafts cannot be cancelled here." }
        val updated = draft.copy(state = DraftState.CANCELLED)
        drafts.remove(draft)
        drafts.add(updated)
        return updated
    }

    fun all(): List<GoalDraft> = drafts.toList()

    companion object {
        const val DEFAULT_SCOPE = "local-only"
    }
}
