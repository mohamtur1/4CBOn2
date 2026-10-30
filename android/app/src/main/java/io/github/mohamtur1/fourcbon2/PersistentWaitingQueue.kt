package io.github.mohamtur1.fourcbon2

/** Why a queue operation was refused. Every refusal names exactly one cause. */
enum class QueueRefusal {
    /** No job with that identifier exists. */
    UNKNOWN_JOB,

    /** That identifier is already used, including by a cancelled job. */
    DUPLICATE_JOB_ID,

    /** [QueueBounds.MAX_ACTIVE_JOBS] waiting jobs are already queued. */
    QUEUE_FULL,

    /** The job is cancelled. Cancellation is terminal. */
    JOB_TERMINAL,

    /** The requested change does not apply to the job's current state. */
    INVALID_STATE_TRANSITION,

    /** The job was queued in this session, so there is nothing to acknowledge. */
    NOT_A_RECOVERED_JOB,
}

/** The result of one queue operation: a stored job, or an explicit refusal. */
sealed interface QueueOutcome {
    data class Stored(val job: LocalQueuedJob) : QueueOutcome

    data class Refused(val jobId: String, val reason: QueueRefusal) : QueueOutcome
}

/**
 * A persistent, local list of jobs the owner wants to do later.
 *
 * **This class cannot run anything.** It has no executor, no scheduler, no network client,
 * and no reference to the capability registry, the scope contract, or the control lease.
 * There is no method whose name or signature could start, dispatch, perform, or resume
 * work — queueing an entry writes one row to the local database and nothing else. That is
 * a structural property, and a reflection test asserts it rather than trusting the comment.
 *
 * **Recovery never replays.** Because the queue holds wishes rather than commands, opening
 * it over an existing file can only ever *report* what is waiting. There is no code path
 * from a recovered row to an action, so "no automatic consequential replay" is guaranteed
 * by the absence of any replay mechanism rather than by a guard that could be forgotten.
 *
 * **Recovery is derived, not stored.** A job counts as recovered from a previous session
 * when its last local owner action predates this instance's session start. Nothing is
 * mutated on open, so opening the queue twice cannot rewrite history. The owner clears a
 * recovered entry explicitly with [acknowledgeRecovered], which records that they have seen
 * it — and still performs no work.
 *
 * Storage is [LocalEntityDatabase] at schema version 3. Queue changes are not paired with
 * `LocalActionEvent` rows because those events are draft-scoped by design (an event must
 * reference an existing draft); a queued job is not a draft, and inventing a synthetic
 * draft to satisfy that constraint would misrepresent the owner's data. The job row itself
 * carries the state and both timestamps instead.
 */
class PersistentWaitingQueue(
    private val database: LocalEntityDatabase,
    private val nowEpochMillis: () -> Long,
) {
    /**
     * The instant this queue instance started.
     *
     * Any waiting job whose last owner action is earlier than this survived a previous run.
     */
    val sessionStartedAtEpochMillis: Long = nowEpochMillis()

    /**
     * Adds one job to the queue.
     *
     * An identifier is refused rather than reused even when the previous job with that
     * identifier was cancelled, so a cancellation keeps its history and a new job cannot
     * silently inherit it.
     */
    fun enqueue(
        id: String,
        summary: String,
        deferral: DeferralReason? = null,
    ): QueueOutcome {
        if (database.findQueuedJob(id) != null) {
            return QueueOutcome.Refused(id, QueueRefusal.DUPLICATE_JOB_ID)
        }
        if (activeCount() >= QueueBounds.MAX_ACTIVE_JOBS) {
            return QueueOutcome.Refused(id, QueueRefusal.QUEUE_FULL)
        }

        val now = nowEpochMillis()
        val job = LocalQueuedJob(
            id = id,
            summary = summary,
            state = QueuedJobState.WAITING,
            deferral = deferral,
            queuedAtEpochMillis = now,
            lastOwnerActionAtEpochMillis = now,
        )
        database.saveQueuedJob(job)
        return QueueOutcome.Stored(job)
    }

    /**
     * Records that the owner deferred a waiting job for a stated reason.
     *
     * The reason is a local observation. Deferring a network task records only that this
     * device had no usable network; it never records an attempt, a request, or a failure.
     */
    fun defer(jobId: String, reason: DeferralReason): QueueOutcome {
        val job = database.findQueuedJob(jobId)
            ?: return QueueOutcome.Refused(jobId, QueueRefusal.UNKNOWN_JOB)
        if (job.state == QueuedJobState.CANCELLED) {
            return QueueOutcome.Refused(jobId, QueueRefusal.JOB_TERMINAL)
        }

        val updated = job.copy(
            deferral = reason,
            lastOwnerActionAtEpochMillis = nextOwnerAction(job),
        )
        database.saveQueuedJob(updated)
        return QueueOutcome.Stored(updated)
    }

    /**
     * Cancels a waiting job. Cancellation is terminal and keeps the row for the record.
     */
    fun cancel(jobId: String): QueueOutcome {
        val job = database.findQueuedJob(jobId)
            ?: return QueueOutcome.Refused(jobId, QueueRefusal.UNKNOWN_JOB)
        if (job.state == QueuedJobState.CANCELLED) {
            return QueueOutcome.Refused(jobId, QueueRefusal.JOB_TERMINAL)
        }

        val updated = job.copy(
            state = QueuedJobState.CANCELLED,
            deferral = null,
            lastOwnerActionAtEpochMillis = nextOwnerAction(job),
        )
        database.saveQueuedJob(updated)
        return QueueOutcome.Stored(updated)
    }

    /**
     * Records that the owner has re-seen a job carried over from a previous session.
     *
     * This is the explicit fresh owner action a recovered entry requires. It stamps the
     * owner's attention and does nothing else: the job stays [QueuedJobState.WAITING] with
     * its deferral intact, and no work begins.
     */
    fun acknowledgeRecovered(jobId: String): QueueOutcome {
        val job = database.findQueuedJob(jobId)
            ?: return QueueOutcome.Refused(jobId, QueueRefusal.UNKNOWN_JOB)
        if (!isRecovered(job)) {
            return QueueOutcome.Refused(jobId, QueueRefusal.NOT_A_RECOVERED_JOB)
        }

        val updated = job.copy(
            lastOwnerActionAtEpochMillis = maxOf(nowEpochMillis(), sessionStartedAtEpochMillis),
        )
        database.saveQueuedJob(updated)
        return QueueOutcome.Stored(updated)
    }

    fun job(jobId: String): LocalQueuedJob? = database.findQueuedJob(jobId)

    /** Every waiting job, ordered by queued time then identifier. Empty when none wait. */
    fun active(): List<LocalQueuedJob> = orderedWaiting(database.queuedJobs())

    /** Waiting jobs carried over from a previous session, in the same order as [active]. */
    fun recovered(): List<LocalQueuedJob> = orderedWaiting(database.queuedJobs()).filter { isRecovered(it) }

    /** How many waiting jobs survived the last restart. Zero means nothing was carried over. */
    fun recoveredCount(): Int = recovered().size

    /** Every job ever queued, including cancelled ones, in queued-time order. */
    fun all(): List<LocalQueuedJob> = database.queuedJobs()

    private fun activeCount(): Int = database.queuedJobs().count { it.state == QueuedJobState.WAITING }

    private fun isRecovered(job: LocalQueuedJob): Boolean =
        job.state == QueuedJobState.WAITING && job.lastOwnerActionAtEpochMillis < sessionStartedAtEpochMillis

    /**
     * The next owner-action stamp.
     *
     * Clamped to at least the job's queued time so a frozen or backwards clock can never
     * produce a job that violates its own ordering invariant.
     */
    private fun nextOwnerAction(job: LocalQueuedJob): Long =
        maxOf(nowEpochMillis(), job.queuedAtEpochMillis)

    private fun orderedWaiting(jobs: List<LocalQueuedJob>): List<LocalQueuedJob> =
        jobs.filter { it.state == QueuedJobState.WAITING }
            .sortedWith(
                compareBy<LocalQueuedJob> { it.queuedAtEpochMillis }.thenBy { it.id },
            )
}
