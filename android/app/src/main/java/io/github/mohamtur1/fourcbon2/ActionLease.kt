package io.github.mohamtur1.fourcbon2

/** Hard bounds for a local control lease; a lease cannot be created outside these. */
object LeaseBounds {
    const val MIN_DURATION_MILLIS = 1L

    /** A lease never outlives the owner-started five-minute job proposal. */
    const val MAX_DURATION_MILLIS = ScopeBounds.MAX_DURATION_MILLIS

    /** Every lease starts at this epoch and increments on each accepted transition. */
    const val INITIAL_EPOCH = 1
}

/** Who issued a control command. Only the local owner surface may loosen control. */
enum class LeaseCommandOrigin {
    OWNER_LOCAL,
    AUTOMATION,
}

enum class LeaseCommandKind {
    TAKE_OVER,
    PAUSE,
    STOP,
    RESUME,
}

/** Lifecycle of the local control lease. [STOPPED] is terminal. */
enum class LeaseState {
    ACTIVE,
    OWNER_TAKEOVER,
    PAUSED,
    STOPPED,
}

/** Why a control command or a step readiness check was refused. */
enum class LeaseRefusal {
    STALE_EPOCH,
    UNISSUED_EPOCH,
    NOT_OWNER_ORIGINATED,
    INVALID_TRANSITION,
    LEASE_TERMINATED,
    LEASE_EXPIRED,
    OWNER_TAKEOVER_ACTIVE,
    PAUSED,
    FRESH_STATE_REQUIRED,
}

/**
 * A locally issued control command. The epoch binds the command to the lease state the
 * owner was actually looking at, so a command written before a takeover cannot later
 * arrive and silently undo it.
 */
data class LeaseCommand(
    val kind: LeaseCommandKind,
    val origin: LeaseCommandOrigin,
    val epoch: Int,
    val issuedAtEpochMillis: Long,
) {
    init {
        require(epoch >= LeaseBounds.INITIAL_EPOCH) { "epoch must be at least ${LeaseBounds.INITIAL_EPOCH}." }
        require(issuedAtEpochMillis > 0) { "issuedAtEpochMillis must be positive." }
    }

    /** Whether this command loosens control (may be issued by the owner only). */
    val isPermissive: Boolean get() = kind == LeaseCommandKind.RESUME
}

/** An atomic, read-only view of the lease for one caller decision. */
data class LeaseSnapshot(
    val state: LeaseState,
    val epoch: Int,
    val refusal: LeaseRefusal?,
) {
    val isReadyForStep: Boolean get() = refusal == null
}

sealed interface LeaseOutcome {
    data class Applied(
        val kind: LeaseCommandKind,
        val previousState: LeaseState,
        val state: LeaseState,
        val epoch: Int,
    ) : LeaseOutcome

    data class Refused(
        val kind: LeaseCommandKind,
        val reason: LeaseRefusal,
        val currentState: LeaseState,
        val currentEpoch: Int,
    ) : LeaseOutcome
}

/**
 * A local, owner-controlled Take Over / Pause / Stop lease for one bounded job.
 *
 * It never depends on a cloud planner: cancellation is decided entirely on device, so
 * losing the network can never make "stop" unavailable. Restrictive commands
 * (`TAKE_OVER`, `PAUSE`, `STOP`) may be issued by the automation itself, because
 * stopping is always safe. The permissive command (`RESUME`) must come from the local
 * owner surface, and a resumed job must additionally pass a fresh state check before
 * any new step runs.
 */
class ActionLease(
    val createdAtEpochMillis: Long,
    val durationMillis: Long = LeaseBounds.MAX_DURATION_MILLIS,
    private val nowEpochMillis: () -> Long,
) {
    init {
        require(createdAtEpochMillis > 0) { "createdAtEpochMillis must be positive." }
        require(durationMillis in LeaseBounds.MIN_DURATION_MILLIS..LeaseBounds.MAX_DURATION_MILLIS) {
            "durationMillis must be between ${LeaseBounds.MIN_DURATION_MILLIS} and ${LeaseBounds.MAX_DURATION_MILLIS}."
        }
    }

    var state: LeaseState = LeaseState.ACTIVE
        private set

    var epoch: Int = LeaseBounds.INITIAL_EPOCH
        private set

    /**
     * A brand-new lease was created by the owner against the current state, so it starts
     * fresh. Any accepted transition (take over, pause, stop, resume) clears this flag and
     * forces an explicit post-resume state check before the next step.
     */
    private var freshStateConfirmed = true

    val expiresAtEpochMillis: Long get() = createdAtEpochMillis + durationMillis

    fun isExpiredAt(nowEpochMillis: Long): Boolean = nowEpochMillis >= expiresAtEpochMillis

    /** Applies one control command, or refuses it with exactly one cause. */
    @Synchronized
    fun apply(command: LeaseCommand): LeaseOutcome {
        val refusal = refusalFor(command)
        if (refusal != null) {
            return LeaseOutcome.Refused(
                kind = command.kind,
                reason = refusal,
                currentState = state,
                currentEpoch = epoch,
            )
        }

        val previous = state
        state = when (command.kind) {
            LeaseCommandKind.TAKE_OVER -> LeaseState.OWNER_TAKEOVER
            LeaseCommandKind.PAUSE -> LeaseState.PAUSED
            LeaseCommandKind.STOP -> LeaseState.STOPPED
            LeaseCommandKind.RESUME -> LeaseState.ACTIVE
        }
        epoch += 1
        freshStateConfirmed = false
        return LeaseOutcome.Applied(
            kind = command.kind,
            previousState = previous,
            state = state,
            epoch = epoch,
        )
    }

    /**
     * Marks the fresh post-resume state check as done for the current epoch. A check
     * from an older epoch is refused, so the automation cannot pre-satisfy a future one.
     */
    @Synchronized
    fun confirmFreshState(confirmingEpoch: Int): LeaseOutcome {
        val refusal = when {
            state == LeaseState.STOPPED -> LeaseRefusal.LEASE_TERMINATED
            isExpiredAt(nowEpochMillis()) -> LeaseRefusal.LEASE_EXPIRED
            confirmingEpoch != epoch -> LeaseRefusal.STALE_EPOCH
            state != LeaseState.ACTIVE -> LeaseRefusal.INVALID_TRANSITION
            else -> null
        }
        if (refusal != null) {
            return LeaseOutcome.Refused(
                kind = LeaseCommandKind.RESUME,
                reason = refusal,
                currentState = state,
                currentEpoch = epoch,
            )
        }
        freshStateConfirmed = true
        return LeaseOutcome.Applied(
            kind = LeaseCommandKind.RESUME,
            previousState = state,
            state = state,
            epoch = epoch,
        )
    }

    @Synchronized
    fun isReadyForStep(nowEpochMillis: Long): Boolean = stepRefusal(nowEpochMillis) == null

    /** One atomic view of the lease state, epoch, and any step refusal. */
    @Synchronized
    fun snapshot(nowEpochMillis: Long): LeaseSnapshot =
        LeaseSnapshot(state = state, epoch = epoch, refusal = stepRefusal(nowEpochMillis))

    @Synchronized
    fun stepRefusal(nowEpochMillis: Long): LeaseRefusal? = when {
        state == LeaseState.STOPPED -> LeaseRefusal.LEASE_TERMINATED
        isExpiredAt(nowEpochMillis) -> LeaseRefusal.LEASE_EXPIRED
        state == LeaseState.OWNER_TAKEOVER -> LeaseRefusal.OWNER_TAKEOVER_ACTIVE
        state == LeaseState.PAUSED -> LeaseRefusal.PAUSED
        !freshStateConfirmed -> LeaseRefusal.FRESH_STATE_REQUIRED
        else -> null
    }

    private fun refusalFor(command: LeaseCommand): LeaseRefusal? = when {
        state == LeaseState.STOPPED -> LeaseRefusal.LEASE_TERMINATED
        command.epoch < epoch -> LeaseRefusal.STALE_EPOCH
        command.epoch > epoch -> LeaseRefusal.UNISSUED_EPOCH
        command.isPermissive && isExpiredAt(nowEpochMillis()) -> LeaseRefusal.LEASE_EXPIRED
        command.isPermissive && command.origin != LeaseCommandOrigin.OWNER_LOCAL -> LeaseRefusal.NOT_OWNER_ORIGINATED
        command.kind == LeaseCommandKind.RESUME && state == LeaseState.ACTIVE -> LeaseRefusal.INVALID_TRANSITION
        command.kind == LeaseCommandKind.PAUSE && state == LeaseState.PAUSED -> LeaseRefusal.INVALID_TRANSITION
        else -> null
    }
}
