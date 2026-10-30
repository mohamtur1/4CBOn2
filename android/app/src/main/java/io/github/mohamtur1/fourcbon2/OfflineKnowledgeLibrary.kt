package io.github.mohamtur1.fourcbon2

/** Why a knowledge-library operation was refused. */
enum class KnowledgeRefusal {
    UNKNOWN_ITEM,
    DUPLICATE_ITEM_ID,
    // An over-long query or an out-of-range limit is a caller programming error, not a
    // runtime refusal: `search` enforces both with `require` and throws rather than
    // returning. Two values that could never be returned used to sit here; they were
    // removed instead of being left as decoration, so the enum no longer promises a
    // refusal path that does not exist.
}

/** One local search hit, plus how many matches existed in total. */
data class KnowledgeSearchResult(
    val query: String,
    val items: List<LocalKnowledgeItem>,
    val totalMatches: Int,
    val truncated: Boolean,
)

/**
 * A bounded, local shelf for saved reports and evidence.
 *
 * The library is **inert**: it holds a database and a clock and nothing else, so saving or
 * reading an item cannot grant a permission, start a job, resume a lease, or bypass a scope
 * contract. It has no network access and no cloud upload.
 *
 * Provenance is recorded, not inferred: an item states only whether the owner supplied it,
 * observed it, or derived it from a local draft. Nothing here asserts that the content is
 * true, current, or verified.
 */
class OfflineKnowledgeLibrary(
    private val database: LocalEntityDatabase,
    private val nowEpochMillis: () -> Long,
) {
    sealed interface SaveResult {
        data class Saved(val item: LocalKnowledgeItem) : SaveResult

        data class Refused(val itemId: String, val reason: KnowledgeRefusal) : SaveResult
    }

    sealed interface ReadResult {
        data class Found(val item: LocalKnowledgeItem) : ReadResult

        data class Refused(val itemId: String, val reason: KnowledgeRefusal) : ReadResult
    }

    /**
     * Saves one item, or refuses without writing anything. A duplicate identifier is
     * refused rather than overwritten, so a saved report cannot be silently replaced.
     */
    fun saveItem(
        id: String,
        title: String,
        body: String,
        sourceLabel: String,
        provenance: KnowledgeProvenance,
        savedAtEpochMillis: Long = nowEpochMillis(),
    ): SaveResult {
        if (database.findKnowledgeItem(id) != null) {
            return SaveResult.Refused(itemId = id, reason = KnowledgeRefusal.DUPLICATE_ITEM_ID)
        }
        val item = LocalKnowledgeItem(
            id = id,
            title = title,
            body = body,
            sourceLabel = sourceLabel,
            provenance = provenance,
            savedAtEpochMillis = savedAtEpochMillis,
        )
        database.saveKnowledgeItem(item)
        return SaveResult.Saved(item)
    }

    fun loadItem(id: String): ReadResult {
        val item = database.findKnowledgeItem(id)
            ?: return ReadResult.Refused(itemId = id, reason = KnowledgeRefusal.UNKNOWN_ITEM)
        return ReadResult.Found(item)
    }

    /** All saved items, oldest first with the identifier as a stable tie-break. */
    fun allItems(): List<LocalKnowledgeItem> = database.knowledgeItems()

    /**
     * Case-insensitive search over title, body, and source label.
     *
     * Ordering is deterministic (saved time, then identifier), the result cap is bounded,
     * and `totalMatches` reports how many items matched so a truncated result is visible
     * rather than silently misleading. An empty query lists everything within the cap.
     */
    fun search(
        query: String,
        limit: Int = KnowledgeSearchBounds.DEFAULT_LIMIT,
    ): KnowledgeSearchResult {
        require(query.length <= KnowledgeSearchBounds.MAX_QUERY_LENGTH) {
            "query must be at most ${KnowledgeSearchBounds.MAX_QUERY_LENGTH} characters."
        }
        require(limit in 1..KnowledgeSearchBounds.MAX_LIMIT) {
            "limit must be between 1 and ${KnowledgeSearchBounds.MAX_LIMIT}."
        }

        val matches = database.knowledgeItems().filter { item -> item.matches(query) }
        return KnowledgeSearchResult(
            query = query,
            items = matches.take(limit),
            totalMatches = matches.size,
            truncated = matches.size > limit,
        )
    }

    /**
     * Derives a knowledge item from an existing local draft, recording
     * [KnowledgeProvenance.LOCAL_DRAFT_DERIVED]. Refuses an unknown draft.
     */
    fun saveFromDraft(
        draftId: String,
        itemId: String,
        sourceLabel: String,
    ): SaveResult {
        val draft = database.findDraft(draftId)
            ?: return SaveResult.Refused(itemId = itemId, reason = KnowledgeRefusal.UNKNOWN_ITEM)
        return saveItem(
            id = itemId,
            title = draft.title,
            body = draft.goal,
            sourceLabel = sourceLabel,
            provenance = KnowledgeProvenance.LOCAL_DRAFT_DERIVED,
            savedAtEpochMillis = maxOf(nowEpochMillis(), draft.createdAtEpochMillis),
        )
    }
}
