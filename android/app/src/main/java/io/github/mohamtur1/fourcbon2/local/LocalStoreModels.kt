package io.github.mohamtur1.fourcbon2.local

/** Bounded resource limits recorded with each job. */
data class ResourceCaps(
    val maxActions: Int,
    val maxUploadBytes: Long,
    val maxMinutes: Long,
)

/** Versioned record of one queued job. */
data class JobRecord(
    val id: String,
    val ownerAccount: String,
    val goal: String,
    val workflowVersion: Int,
    val inputs: Map<String, String>,
    val artifactHashes: Map<String, String>,
    val baseRevision: String,
    val resourceCaps: ResourceCaps,
    val dependencies: List<String>,
    val deadlineEpochMs: Long,
    val authorizationScope: String,
    val state: JobState,
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > deadlineEpochMs
}

/** Offline goal draft with its lifecycle state. */
data class GoalDraft(
    val id: String,
    val title: String,
    val goalText: String,
    val templateId: String,
    val budget: Int,
    val permissionScope: String,
    val state: DraftState,
)

enum class DraftState { DRAFT, TEMPLATE_INSTANTIATED, READY_TO_QUEUE, QUEUED, CANCELLED }

/** One saved knowledge entry with provenance. */
data class KnowledgeEntry(
    val id: String,
    val title: String,
    val source: String,
    val provenance: String,
    val content: String,
    val updatedAtEpochMs: Long,
)

/** Durable approval request for a sensitive action. */
data class ApprovalRequest(
    val id: String,
    val actionDescription: String,
    val destination: String,
    val artifactHash: String,
    val expiryEpochMs: Long,
    val maxActions: Int,
    val state: ApprovalState,
)

enum class ApprovalState { PENDING, GRANTED, DENIED, EXPIRED, CANCELLED }

/** Event kinds recorded on the action timeline. */
enum class TimelineEventKind { PLAN, ACTION, RESULT, CHECK }

enum class TimelineEventStatus { ATTEMPTED, PENDING, MANUAL, VERIFIED, UNCERTAIN }

data class TimelineEvent(
    val id: String,
    val kind: TimelineEventKind,
    val description: String,
    val status: TimelineEventStatus,
    val occurredAtEpochMs: Long,
)
