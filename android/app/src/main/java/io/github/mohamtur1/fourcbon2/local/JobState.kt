package io.github.mohamtur1.fourcbon2.local

/**
 * Durable job states for the offline-first job queue.
 *
 * Consequential external work is never replayed automatically: only jobs
 * explicitly moved to [READY] are dispatched, and uncertain outcomes must
 * be reconciled before any retry.
 */
enum class JobState {
    DRAFT,
    WAITING_FOR_INTERNET,
    WAITING_FOR_ACCOUNT,
    WAITING_FOR_RUNTIME,
    READY,
    RUNNING,
    WAITING_FOR_APPROVAL,
    UNCERTAIN_EXTERNAL_OUTCOME,
    COMPLETED,
    FAILED,
    CANCELLED,
    ;

    /** True when the job may be dispatched by an authorized runner. */
    val isDispatchable: Boolean
        get() = this == READY

    /** True when the job must be reconciled by the owner before any retry. */
    val requiresReconciliation: Boolean
        get() = this == UNCERTAIN_EXTERNAL_OUTCOME
}
