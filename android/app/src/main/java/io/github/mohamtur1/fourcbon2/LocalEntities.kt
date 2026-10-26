package io.github.mohamtur1.fourcbon2

private val LOCAL_IDENTIFIER = Regex("[A-Za-z0-9._:-]{1,64}")

private fun validateIdentifier(value: String, fieldName: String) {
    require(LOCAL_IDENTIFIER.matches(value)) {
        "$fieldName must be 1-64 characters using letters, digits, dot, underscore, colon, or hyphen."
    }
}

private fun validateText(
    value: String,
    fieldName: String,
    maxLength: Int,
    allowBlank: Boolean = false,
) {
    require(allowBlank || value.isNotBlank()) { "$fieldName must not be blank." }
    require(value.length <= maxLength) { "$fieldName must be at most $maxLength characters." }
    require(value.none { it == '\n' || it == '\r' || it == '\t' }) {
        "$fieldName must not contain control separators."
    }
}

enum class LocalDraftState {
    DRAFT,
    WAITING_FOR_REVIEW,
    CANCELLED,
}

/**
 * A minimal offline agent draft. It deliberately has no account, credential,
 * service-response, cloud-sync, or unrestricted telemetry fields.
 */
data class LocalAgentDraft(
    val id: String,
    val title: String,
    val goal: String,
    val templateName: String,
    val state: LocalDraftState,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        validateIdentifier(id, "id")
        validateText(title, "title", 120)
        validateText(goal, "goal", 1_000)
        validateText(templateName, "templateName", 80)
        require(createdAtEpochMillis > 0) { "createdAtEpochMillis must be positive." }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "updatedAtEpochMillis must not precede createdAtEpochMillis."
        }
    }
}

enum class LocalActionKind {
    DRAFT_CREATED,
    DRAFT_UPDATED,
    LOCAL_CHECK_RECORDED,
    MANUAL_HANDOFF_PREPARED,
}

enum class LocalActionOutcome {
    PLANNED,
    COMPLETED,
    CANCELLED,
    FAILED,
}

/**
 * A short, fixed-kind local event tied to a draft. Its note is a synthetic or
 * owner-visible label, never a secret, raw service response, or screen capture.
 */
data class LocalActionEvent(
    val id: String,
    val draftId: String,
    val kind: LocalActionKind,
    val outcome: LocalActionOutcome,
    val note: String,
    val createdAtEpochMillis: Long,
) {
    init {
        validateIdentifier(id, "id")
        validateIdentifier(draftId, "draftId")
        validateText(note, "note", 160, allowBlank = true)
        require(createdAtEpochMillis > 0) { "createdAtEpochMillis must be positive." }
    }
}
