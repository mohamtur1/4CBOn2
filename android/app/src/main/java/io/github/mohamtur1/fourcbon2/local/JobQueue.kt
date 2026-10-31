package io.github.mohamtur1.fourcbon2.local

/**
 * Persistent waiting queue (Division 19).
 *
 * Jobs survive restarts; consequential work is never replayed automatically
 * after a restart — a job is dispatchable only when it is explicitly Ready
 * and still eligible (not expired, not cancelled, not uncertain).
 */
class JobQueue(
    private val store: LocalDocumentStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val jobs = mutableListOf<JobRecord>()

    fun load(): List<JobRecord> {
        jobs.clear()
        jobs.addAll(store.readCollection("jobs") { JobRecordCodec.decode(it) })
        return jobs.toList()
    }

    fun add(job: JobRecord) {
        require(job.id.isNotBlank()) { "Job id is required." }
        require(jobs.none { it.id == job.id }) { "Duplicate job id: ${job.id}" }
        jobs.add(job)
        persist()
    }

    fun list(state: JobState? = null): List<JobRecord> =
        if (state == null) jobs.toList() else jobs.filter { it.state == state }

    fun transition(id: String, newState: JobState): JobRecord? {
        val job = jobs.find { it.id == id } ?: return null
        val updated = job.copy(state = newState)
        jobs.remove(job)
        jobs.add(updated)
        persist()
        return updated
    }

    /** Jobs that may be dispatched: explicitly Ready and still eligible. */
    fun dispatchable(): List<JobRecord> = jobs.filter { it.state.isDispatchable && isEligible(it) }

    fun isEligible(job: JobRecord): Boolean =
        !job.isExpired &&
            job.state != JobState.CANCELLED &&
            job.state != JobState.UNCERTAIN_EXTERNAL_OUTCOME &&
            job.dependencies.all { dep -> jobs.any { it.id == dep && it.state == JobState.COMPLETED } }

    /**
     * Reconnection reconciliation (Division 19). Safe local drafts and
     * permitted cached metadata may synchronize; expired, cancelled, or
     * uncertain jobs are rejected and never silently replayed.
     */
    fun reconcileOnReconnect(): List<JobRecord> {
        val reconciled = mutableListOf<JobRecord>()
        for (job in jobs.toList()) {
            when {
                job.isExpired -> reconciled.add(transition(job.id, JobState.FAILED) ?: job)
                job.state == JobState.UNCERTAIN_EXTERNAL_OUTCOME -> {
                    // Uncertain outcomes stay uncertain until the owner reconciles.
                    reconciled.add(job)
                }
                else -> reconciled.add(job)
            }
        }
        return reconciled
    }

    fun markUncertain(id: String): JobRecord? =
        transition(id, JobState.UNCERTAIN_EXTERNAL_OUTCOME)

    fun complete(id: String): JobRecord? = transition(id, JobState.COMPLETED)

    fun cancel(id: String): JobRecord? = transition(id, JobState.CANCELLED)

    fun prepareForDispatch(id: String): JobRecord? {
        val job = jobs.find { it.id == id } ?: return null
        require(job.state == JobState.DRAFT || job.state == JobState.WAITING_FOR_INTERNET) {
            "Only draft or waiting jobs can be prepared for dispatch."
        }
        return transition(id, JobState.READY)
    }

    private fun persist() {
        store.writeCollection("jobs", jobs) { JobRecordCodec.encode(it) }
    }
}
