package io.github.mohamtur1.fourcbon2

/**
 * The combined result of asking whether one step of a bounded job may run.
 *
 * Lease checks come first: a stopped, taken-over, paused, expired, or not-yet-rechecked
 * job is refused without consulting the capability registry at all.
 */
sealed interface JobStepDecision {
    val leaseEpoch: Int

    data class Permitted(
        override val leaseEpoch: Int,
        val scope: ScopeDecision.Permitted,
    ) : JobStepDecision

    data class RefusedByLease(
        override val leaseEpoch: Int,
        val packageName: String,
        val actionId: String,
        val reason: LeaseRefusal,
    ) : JobStepDecision

    data class RefusedByScope(
        override val leaseEpoch: Int,
        val scope: ScopeDecision.Refused,
    ) : JobStepDecision
}

/** Whether a late result may still be applied to the job. */
sealed interface ResultAcceptance {
    data class Accepted(val epoch: Int) : ResultAcceptance

    data class Discarded(
        val submittedEpoch: Int,
        val currentEpoch: Int,
        val reason: LeaseRefusal,
    ) : ResultAcceptance
}

/**
 * Ties one [ActionLease] to one [ActionScopeEnforcer] so a bounded job can only act
 * while the owner-facing lease says it may.
 *
 * Safety properties:
 * - Lease readiness is evaluated before scope, so a paused or taken-over job causes no
 *   capability discovery.
 * - Completion requires the caller's epoch to equal the current lease epoch, so work
 *   started before a takeover cannot land afterwards.
 * - Resuming never restores earlier permissions: every step is re-checked against the
 *   same [ScopeContract], including its action budget and expiry.
 */
class BoundedJobController(
    val lease: ActionLease,
    private val enforcer: ActionScopeEnforcer,
    private val nowEpochMillis: () -> Long,
) {
    fun authorizeStep(
        packageName: String,
        actionId: String,
        cloudTransfer: Boolean = false,
    ): JobStepDecision {
        val snapshot = lease.snapshot(nowEpochMillis())
        snapshot.refusal?.let { refusal ->
            return JobStepDecision.RefusedByLease(
                leaseEpoch = snapshot.epoch,
                packageName = packageName,
                actionId = actionId,
                reason = refusal,
            )
        }

        return when (val scope = enforcer.authorize(packageName, actionId, cloudTransfer)) {
            is ScopeDecision.Permitted -> JobStepDecision.Permitted(
                leaseEpoch = snapshot.epoch,
                scope = scope,
            )

            is ScopeDecision.Refused -> JobStepDecision.RefusedByScope(
                leaseEpoch = snapshot.epoch,
                scope = scope,
            )
        }
    }

    /**
     * Records a completed step against the epoch it was authorized under. A stale epoch
     * is discarded rather than recorded, and the scope budget is not consumed.
     */
    fun recordCompletion(
        authorizedEpoch: Int,
        packageName: String,
        actionId: String,
        cloudTransfer: Boolean = false,
    ): JobStepDecision {
        val currentEpoch = lease.snapshot(nowEpochMillis()).epoch
        if (authorizedEpoch != currentEpoch) {
            return JobStepDecision.RefusedByLease(
                leaseEpoch = currentEpoch,
                packageName = packageName,
                actionId = actionId,
                reason = LeaseRefusal.STALE_EPOCH,
            )
        }

        val decision = authorizeStep(packageName, actionId, cloudTransfer)
        if (decision !is JobStepDecision.Permitted) return decision

        return JobStepDecision.Permitted(
            leaseEpoch = currentEpoch,
            scope = enforcer.recordCompletion(packageName, actionId, cloudTransfer),
        )
    }

    /** Applies a late result only when it belongs to the current lease epoch. */
    fun acceptResult(submittedEpoch: Int): ResultAcceptance {
        val snapshot = lease.snapshot(nowEpochMillis())
        val currentEpoch = snapshot.epoch
        if (submittedEpoch != currentEpoch) {
            return ResultAcceptance.Discarded(
                submittedEpoch = submittedEpoch,
                currentEpoch = currentEpoch,
                reason = if (submittedEpoch < currentEpoch) LeaseRefusal.STALE_EPOCH else LeaseRefusal.UNISSUED_EPOCH,
            )
        }
        val refusal = snapshot.refusal
        if (refusal != null) {
            return ResultAcceptance.Discarded(
                submittedEpoch = submittedEpoch,
                currentEpoch = currentEpoch,
                reason = refusal,
            )
        }
        return ResultAcceptance.Accepted(epoch = currentEpoch)
    }
}
