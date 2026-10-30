package io.github.mohamtur1.fourcbon2

private val KNOWLEDGE_IDENTIFIER = Regex("[A-Za-z0-9._:-]{1,64}")

private fun validateKnowledgeText(
    value: String,
    fieldName: String,
    maxLength: Int,
    allowBlank: Boolean = false,
) {
    require(allowBlank || value.isNotBlank()) { "$fieldName must not be blank." }
    require(value.length <= maxLength) { "$fieldName must be at most $maxLength characters." }
    require(value.none { character -> character == '\n' || character == '\r' || character == '\t' }) {
        "$fieldName must not contain control separators."
    }
}

/**
 * Where one saved knowledge item came from.
 *
 * Deliberately narrow: every value describes something the owner or this device actually
 * did. There is no `SERVICE_RESPONSE`, no `MODEL_OUTPUT`, and no `CITATION` value, because
 * this division has no network access and cannot honestly claim one.
 */
enum class KnowledgeProvenance {
    /** The owner typed or pasted the text themselves. */
    OWNER_SUPPLIED,

    /** The owner recorded something they observed on this device. */
    OWNER_OBSERVED,

    /** Derived from a local draft that already existed in the local database. */
    LOCAL_DRAFT_DERIVED,
}

/**
 * A bounded, locally saved knowledge item: a short report or a piece of evidence.
 *
 * It carries provenance rather than trust. Nothing here asserts that the content is true,
 * current, or verified; it records only where it came from and when it was saved.
 * No account, credential, service response, or cloud-sync field exists.
 */
data class LocalKnowledgeItem(
    val id: String,
    val title: String,
    val body: String,
    val sourceLabel: String,
    val provenance: KnowledgeProvenance,
    val savedAtEpochMillis: Long,
) {
    companion object {
        const val MAX_TITLE_LENGTH = 120
        const val MAX_BODY_LENGTH = 4_000
        const val MAX_SOURCE_LABEL_LENGTH = 120
    }

    init {
        require(KNOWLEDGE_IDENTIFIER.matches(id)) {
            "id must be 1-64 characters using letters, digits, dot, underscore, colon, or hyphen."
        }
        validateKnowledgeText(title, "title", MAX_TITLE_LENGTH)
        validateKnowledgeText(body, "body", MAX_BODY_LENGTH, allowBlank = true)
        validateKnowledgeText(sourceLabel, "sourceLabel", MAX_SOURCE_LABEL_LENGTH)
        require(savedAtEpochMillis > 0L) { "savedAtEpochMillis must be positive." }
    }

    /** Case-insensitive containment over the owner-visible text fields only. */
    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        return title.lowercase().contains(needle) ||
            body.lowercase().contains(needle) ||
            sourceLabel.lowercase().contains(needle)
    }
}

/** Hard bounds for one local knowledge search. */
object KnowledgeSearchBounds {
    const val DEFAULT_LIMIT = 20
    const val MAX_LIMIT = 50
    const val MAX_QUERY_LENGTH = 120
}
