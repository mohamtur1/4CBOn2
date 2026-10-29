package io.github.mohamtur1.fourcbon2

/**
 * Enforces one [ScopeContract] against the owner-selected capability registry.
 *
 * Every check fails closed and names exactly one cause. The order is deliberate:
 * expiry, explicit exclusions, target/action membership, cloud transfer, and the
 * action budget are all decided **before** the registry is consulted, so an
 * out-of-scope or exhausted request causes no discovery at all.
 *
 * Use [authorize] to ask whether a step is allowed, and [recordCompletion] once the
 * owner has confirmed it (or it finished within the allowed automatic mode). Only
 * [recordCompletion] consumes the bounded action budget.
 */
class ActionScopeEnforcer(
    val contract: ScopeContract,
    private val registry: SelectedAppCapabilityRegistry,
    private val nowEpochMillis: () -> Long,
) {
    private var completedActions = 0

    @Synchronized
    fun authorize(
        packageName: String,
        actionId: String,
        cloudTransfer: Boolean = false,
    ): ScopeDecision {
        val now = nowEpochMillis()
        if (contract.isExpiredAt(now)) {
            return refused(packageName, actionId, ScopeRefusal.SCOPE_EXPIRED)
        }
        if (contract.excludedPackages.contains(packageName)) {
            return refused(packageName, actionId, ScopeRefusal.TARGET_EXCLUDED)
        }
        if (contract.excludedActionIds.contains(actionId)) {
            return refused(packageName, actionId, ScopeRefusal.ACTION_EXCLUDED)
        }
        val target = contract.targetFor(packageName)
            ?: return refused(packageName, actionId, ScopeRefusal.TARGET_NOT_IN_SCOPE)
        if (!target.allows(actionId)) {
            return refused(packageName, actionId, ScopeRefusal.ACTION_NOT_IN_SCOPE)
        }
        if (cloudTransfer && !contract.cloudTransferAllowed) {
            return refused(packageName, actionId, ScopeRefusal.CLOUD_TRANSFER_NOT_PERMITTED)
        }
        if (completedActions >= contract.limits.maxActions) {
            return refused(packageName, actionId, ScopeRefusal.ACTION_LIMIT_REACHED)
        }

        return when (val capability = registry.resolve(packageName, actionId)) {
            is CapabilityDecision.Unavailable -> refused(
                packageName = packageName,
                actionId = actionId,
                reason = ScopeRefusal.CAPABILITY_UNAVAILABLE,
                detail = capability.reason.name,
            )

            is CapabilityDecision.Supported -> ScopeDecision.Permitted(
                scopeId = contract.id,
                packageName = packageName,
                actionId = actionId,
                route = capability.route,
                sensitivity = capability.sensitivity,
                requiresOwnerConfirmation =
                    contract.completionMode == CompletionMode.OWNER_CONFIRMS_EACH_STEP ||
                        capability.route == ActionRoute.MANUAL_HANDOFF,
                cloudTransfer = cloudTransfer,
            )
        }
    }

    /**
     * Records one completed, owner-confirmed step and consumes one unit of the action
     * budget. Throws instead of recording a step that the contract refuses.
     */
    @Synchronized
    fun recordCompletion(
        packageName: String,
        actionId: String,
        cloudTransfer: Boolean = false,
    ): ScopeDecision.Permitted {
        val decision = authorize(packageName, actionId, cloudTransfer)
        check(decision is ScopeDecision.Permitted) {
            val refusal = decision as ScopeDecision.Refused
            "Action $packageName/$actionId cannot be recorded: ${refusal.reason}" +
                (refusal.detail?.let { detail -> " ($detail)" } ?: "")
        }
        completedActions += 1
        return decision
    }

    @Synchronized
    fun completedActionCount(): Int = completedActions

    @Synchronized
    fun remainingActions(): Int = (contract.limits.maxActions - completedActions).coerceAtLeast(0)

    @Synchronized
    fun state(): ScopeState = when {
        contract.isExpiredAt(nowEpochMillis()) -> ScopeState.EXPIRED
        completedActions >= contract.limits.maxActions -> ScopeState.EXHAUSTED
        else -> ScopeState.ACTIVE
    }

    private fun refused(
        packageName: String,
        actionId: String,
        reason: ScopeRefusal,
        detail: String? = null,
    ): ScopeDecision.Refused = ScopeDecision.Refused(
        scopeId = contract.id,
        packageName = packageName,
        actionId = actionId,
        reason = reason,
        detail = detail,
    )
}
