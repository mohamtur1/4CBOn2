package io.github.mohamtur1.fourcbon2

private val QUEUE_IDENTIFIER = Regex("[A-Za-z0-9._:-]{1,64}")

/**
 * Where a queued job stands.
 *
 * There is deliberately **no** running, attempted, completed, or failed value. A queue entry
 * records a wish, not an execution, and a state that could be read as "this already ran"
 * would let a restarted process imply work it never did. Terminal is [CANCELLED]: a
 * cancelled job is kept so the owner can see it was dropped, and it never becomes active
 * again.
 */
enum class QueuedJobState {
    /** In the queue, waiting for the owner. */
    WAITING,

    /** Terminal. Removed from the active queue; never runs. */
    CANCELLED,
}

/**
 * Why a job is specifically deferred rather than plainly waiting.
 *
 * Each value describes a **local observation**, not a remote outcome. `NETWORK_UNAVAILABLE`
 * means this device could not reach the network when the owner looked; it does not mean a
 * request was attempted, sent, or failed. That distinction is the whole point: a deferred
 * network task is never recorded as if something happened.
 */
enum class DeferralReason {
    /** The owner deferred this because the device had no usable network at the time. */
    NETWORK_UNAVAILABLE,

    /** The owner deferred this to decide later. */
    AWAITING_OWNER_DECISION,

    /** The owner deferred this until a permission they control is granted. */
    AWAITING_PERMISSION,
}

/**
 * One job waiting in the local queue.
 *
 * It holds a bounded summary of what the owner wants, not an executable instruction: there
 * is no package name, URL, intent, command, path, or credential field, and nothing here can
 * be interpreted by a machine into an action.
 *
 * [lastOwnerActionAtEpochMillis] is the timestamp of the most recent **local owner
 * interaction** with this entry — when it was queued, or when the owner later acknowledged
 * it after a restart. It is what makes recovery detectable without storing a session flag:
 * an entry whose last owner action predates the current session start was carried over from
 * a previous run.
 */
data class LocalQueuedJob(
    val id: String,
    val summary: String,
    val state: QueuedJobState,
    val deferral: DeferralReason?,
    val queuedAtEpochMillis: Long,
    val lastOwnerActionAtEpochMillis: Long,
) {
    companion object {
        const val MAX_SUMMARY_LENGTH = 200
    }

    init {
        require(QUEUE_IDENTIFIER.matches(id)) {
            "id must be 1-64 characters using letters, digits, dot, underscore, colon, or hyphen."
        }
        require(summary.isNotBlank()) { "summary must not be blank." }
        require(summary.length <= MAX_SUMMARY_LENGTH) {
            "summary must be at most $MAX_SUMMARY_LENGTH characters."
        }
        require(summary.none { character -> character == '\n' || character == '\r' || character == '\t' }) {
            "summary must not contain control separators."
        }
        require(queuedAtEpochMillis > 0L) { "queuedAtEpochMillis must be positive." }
        require(lastOwnerActionAtEpochMillis >= queuedAtEpochMillis) {
            "lastOwnerActionAtEpochMillis must not precede queuedAtEpochMillis."
        }
        require(state == QueuedJobState.WAITING || deferral == null) {
            "a terminal job must not carry a deferral reason."
        }
    }
}

/** Hard caps for the waiting queue. Nothing can widen them from outside. */
object QueueBounds {
    /** Largest number of simultaneously active (waiting) jobs. */
    const val MAX_ACTIVE_JOBS = 100

    /** Largest accepted summary length, matching [LocalQueuedJob]. */
    const val MAX_SUMMARY_LENGTH = LocalQueuedJob.MAX_SUMMARY_LENGTH
}
