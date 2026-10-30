package io.github.mohamtur1.fourcbon2

private val ACTION_PACKAGE = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")
private val ACTION_ID = Regex("[a-z][a-z0-9._-]{0,63}")

/** Hard caps for one bounded app action. Nothing can widen them from outside. */
object ActionBounds {
    /**
     * Longest a single platform round trip may take before its answer stops counting.
     *
     * A call that overruns this is not reported as success or failure: a late answer cannot
     * be trusted to describe the bounded window the owner was promised, so the outcome
     * becomes [ActionOutcome.Unknown].
     */
    const val MAX_ROUND_TRIP_MILLIS = 5_000L

    /** Longest accepted action identifier, matching [AppActionCapability]. */
    const val MAX_ACTION_ID_LENGTH = 64
}

/**
 * One fixed, owner-visible request to open one declared entry point of one selected app.
 *
 * There is no free-text command, no URI, no intent string, no chooser, and no arbitrary
 * package: every field is validated against a strict pattern and must already be known to
 * the capability registry. A caller cannot smuggle a destination through this type.
 */
data class AppOpenRequest(
    val packageName: String,
    val actionId: String,
) {
    init {
        require(ACTION_PACKAGE.matches(packageName)) {
            "packageName must be a dotted Android package name such as com.example.app."
        }
        require(ACTION_ID.matches(actionId)) {
            "actionId must be 1-64 lowercase letters, digits, dot, underscore, or hyphen, starting with a letter."
        }
    }
}

/**
 * What the platform boundary reported for one dispatch.
 *
 * This is a **port-level** report, not an outcome. [BoundedAppAction] converts it into an
 * [ActionOutcome] and applies the deadline; a port must never claim an effect it cannot
 * observe.
 */
sealed interface DispatchReport {
    /** The platform accepted the request and reported that the action was delivered. */
    data class Delivered(val detail: String) : DispatchReport

    /**
     * The platform refused and definitely did nothing.
     *
     * Only use this when the platform's own answer proves no effect occurred. If it is
     * merely likely that nothing happened, report [InDoubt].
     */
    data class Rejected(val detail: String) : DispatchReport

    /**
     * The boundary cannot say whether the action took effect.
     *
     * This is the honest default for a timeout, a dropped connection, a malformed response,
     * or an exception whose position in the call is unknown.
     */
    data class InDoubt(val detail: String) : DispatchReport
}

/**
 * The narrow platform seam for one harmless open.
 *
 * Implementations perform **exactly** the dispatch they are given and answer with a
 * [DispatchReport]. They must not retry, queue, escalate to a broader intent, fall back to a
 * different action, or echo anything into a log.
 *
 * [NONE] is the default and the only implementation this build wires: it refuses every
 * request without touching anything, so the shipped app cannot dispatch at all. A real
 * implementation is a separate, reviewable decision.
 *
 * **This boundary is untested against a real platform.** CI runs on a plain JVM with no
 * Android framework, so every test here drives a fake port. See `android/APP_ACTION.md`.
 */
fun interface AppActionPort {
    fun dispatch(request: AppOpenRequest): DispatchReport

    companion object {
        /** Safe default: nothing is dispatched, and the refusal is explicit. */
        val NONE: AppActionPort = AppActionPort {
            DispatchReport.Rejected("No platform port is configured in this build.")
        }
    }
}

/** Why a request was refused **before** any dispatch was attempted. */
enum class ActionRefusal {
    /** The package name or action id is not a bounded, valid identifier. */
    REQUEST_INVALID,

    /** The registry does not currently support this action for this app. */
    CAPABILITY_UNAVAILABLE,

    /** The only supported route leaves the step to the owner, so there is nothing to dispatch. */
    MANUAL_STEP_ONLY,

    /** The job scope refused this target or action. */
    SCOPE_REFUSED,

    /** The control lease is not ready for a step. */
    LEASE_NOT_READY,

    /**
     * The request describes a gated act (a cloud transfer, or a share route), which this
     * division does not handle at all. It is refused rather than performed without an
     * approval.
     */
    APPROVAL_REQUIRED,

    /** The resolved route is not an open this division dispatches. */
    ROUTE_NOT_DISPATCHABLE,
}

/**
 * The honest result of one attempt.
 *
 * The four cases exist because collapsing any two of them would mislead the owner:
 *
 * - [Refused] — **nothing was attempted.** Guaranteed: it is only produced before the port
 *   is called.
 * - [Completed] — the platform confirmed the action was delivered.
 * - [Failed] — a dispatch was attempted and the platform proved nothing happened.
 * - [Unknown] — a dispatch was attempted and the result could not be determined. This is
 *   never reported as success or failure.
 */
sealed interface ActionOutcome {
    val packageName: String
    val actionId: String

    data class Refused(
        override val packageName: String,
        override val actionId: String,
        val reason: ActionRefusal,
    ) : ActionOutcome {
        /** A fixed phrase. It never repeats a platform detail or a destination. */
        val detail: String get() = when (reason) {
            ActionRefusal.REQUEST_INVALID -> "The request is not a bounded, valid identifier."
            ActionRefusal.CAPABILITY_UNAVAILABLE -> "This app has not declared a usable route for this action."
            ActionRefusal.MANUAL_STEP_ONLY -> "This action leaves the step to the owner, so nothing was dispatched."
            ActionRefusal.SCOPE_REFUSED -> "The job scope does not permit this target or action."
            ActionRefusal.LEASE_NOT_READY -> "The control lease is not ready for a step."
            ActionRefusal.APPROVAL_REQUIRED -> "This act needs an owner approval this division does not handle."
            ActionRefusal.ROUTE_NOT_DISPATCHABLE -> "This route is not an open this division dispatches."
        }
    }

    data class Completed(
        override val packageName: String,
        override val actionId: String,
        val route: ActionRoute,
        val elapsedMillis: Long,
    ) : ActionOutcome

    data class Failed(
        override val packageName: String,
        override val actionId: String,
        val elapsedMillis: Long,
    ) : ActionOutcome

    data class Unknown(
        override val packageName: String,
        override val actionId: String,
        val elapsedMillis: Long,
        val cause: UnknownCause,
    ) : ActionOutcome

    /** Why the result could not be determined. Never states that anything happened. */
    enum class UnknownCause {
        /** The round trip exceeded [ActionBounds.MAX_ROUND_TRIP_MILLIS]. */
        DEADLINE_EXCEEDED,

        /** The platform boundary threw, so the effect position is unknown. */
        BOUNDARY_ERROR,

        /** The boundary itself reported that it could not say. */
        BOUNDARY_IN_DOUBT,
    }
}

/**
 * Performs **one** harmless open of one owner-declared entry point.
 *
 * ## What makes this act harmless
 *
 * The action opens a screen the owner already selected and explicitly declared in
 * [SelectedAppCapabilityRegistry]. It sends nothing, buys nothing, deletes nothing, posts
 * nothing, and writes nothing outside the target app. The worst case if it misfires is that
 * an app opens to a screen the owner did not want — visible, harmless, and reversible by
 * going back.
 *
 * Only [ActionRoute.APP_INTENT] is dispatched. A link-or-share route is refused with
 * [ActionRefusal.APPROVAL_REQUIRED] because a share is an approval-gated act, and a manual
 * handoff is refused with [ActionRefusal.MANUAL_STEP_ONLY] because the owner performs it.
 * Gated acts such as a cloud transfer are likewise refused rather than performed. This class
 * has no parameter that would let it redeem a [SensitiveActionApproval], so there is no path
 * from here to an ungated sensitive act.
 *
 * ## Composition
 *
 * Every check runs before the port is called, in a fixed documented order:
 * request shape → capability → route → scope → lease → gated-act check. A refusal therefore
 * guarantees nothing was dispatched. The registry, the scope enforcer, and the lease are
 * each consulted as the authority for their own question; none is re-implemented here.
 *
 * ## Honesty
 *
 * There is **no retry**. Retrying after an [ActionOutcome.Unknown] could perform the act
 * twice, so an unknown result is reported and left alone. The only method that can produce
 * [ActionOutcome.Completed] is [perform], and it does so only on a `Delivered` report that
 * arrived inside the deadline.
 */
class BoundedAppAction(
    private val registry: SelectedAppCapabilityRegistry,
    private val scopeEnforcer: ActionScopeEnforcer,
    private val lease: ActionLease,
    private val port: AppActionPort = AppActionPort.NONE,
    private val nowEpochMillis: () -> Long,
) {
    private companion object {
        /**
         * The only routes this division will dispatch.
         *
         * [ActionRoute.APP_INTENT] opens a screen. Every other route is excluded on purpose:
         * `APP_LINK_OR_SHARE` is a share and needs the approval gate, `MANUAL_HANDOFF` leaves
         * the step to the owner, and the remaining two are not screen opens.
         */
        val DISPATCHABLE_ROUTES = setOf(ActionRoute.APP_INTENT)
    }

    /**
     * Attempts the action, or refuses without attempting it.
     *
     * [cloudTransfer] exists so a caller cannot quietly reclassify a transfer as an open:
     * asking for one is refused outright rather than performed.
     */
    fun perform(
        packageName: String,
        actionId: String,
        cloudTransfer: Boolean = false,
    ): ActionOutcome {
        val refusal = preflightRefusal(packageName, actionId, cloudTransfer)
        if (refusal != null) {
            return ActionOutcome.Refused(packageName, actionId, refusal)
        }

        val request = AppOpenRequest(packageName, actionId)
        val startedAt = nowEpochMillis()

        val report = try {
            port.dispatch(request)
        } catch (error: Exception) {
            // The call threw. We cannot know whether it threw before or after the platform
            // acted, so this is never reported as a failure.
            return ActionOutcome.Unknown(
                packageName = packageName,
                actionId = actionId,
                elapsedMillis = elapsedSince(startedAt),
                cause = ActionOutcome.UnknownCause.BOUNDARY_ERROR,
            )
        }

        val elapsed = elapsedSince(startedAt)
        if (elapsed > ActionBounds.MAX_ROUND_TRIP_MILLIS) {
            // The answer arrived outside the promised window, so it is not treated as an
            // answer. Reporting it as success or failure would overstate what we know.
            return ActionOutcome.Unknown(
                packageName = packageName,
                actionId = actionId,
                elapsedMillis = elapsed,
                cause = ActionOutcome.UnknownCause.DEADLINE_EXCEEDED,
            )
        }

        return when (report) {
            is DispatchReport.Delivered -> ActionOutcome.Completed(
                packageName = packageName,
                actionId = actionId,
                route = resolvedRoute(packageName, actionId),
                elapsedMillis = elapsed,
            )

            is DispatchReport.Rejected -> ActionOutcome.Failed(packageName, actionId, elapsed)

            is DispatchReport.InDoubt -> ActionOutcome.Unknown(
                packageName = packageName,
                actionId = actionId,
                elapsedMillis = elapsed,
                cause = ActionOutcome.UnknownCause.BOUNDARY_IN_DOUBT,
            )
        }
    }

    /**
     * The route this action would take, or null when it cannot be dispatched.
     *
     * Exposed so an owner-visible preview can show exactly what would be opened without
     * attempting anything.
     */
    fun previewRoute(packageName: String, actionId: String): ActionRoute? {
        if (preflightRefusal(packageName, actionId, cloudTransfer = false) != null) return null
        return resolvedRoute(packageName, actionId)
    }

    /** Elapsed time, clamped at zero so a backwards clock cannot look like a deadline breach. */
    private fun elapsedSince(startedAt: Long): Long = (nowEpochMillis() - startedAt).coerceAtLeast(0L)

    private fun resolvedRoute(packageName: String, actionId: String): ActionRoute =
        when (val decision = registry.resolve(packageName, actionId)) {
            is CapabilityDecision.Supported -> decision.route
            is CapabilityDecision.Unavailable -> error("A resolved route was required but the capability is unavailable.")
        }

    private fun preflightRefusal(
        packageName: String,
        actionId: String,
        cloudTransfer: Boolean,
    ): ActionRefusal? {
        if (!ACTION_PACKAGE.matches(packageName) || !ACTION_ID.matches(actionId)) {
            return ActionRefusal.REQUEST_INVALID
        }

        val capability = registry.resolve(packageName, actionId)
        if (capability !is CapabilityDecision.Supported) {
            return ActionRefusal.CAPABILITY_UNAVAILABLE
        }
        // A manual handoff means the owner performs the final step; there is nothing for this
        // class to dispatch, and pretending otherwise would be the worst kind of overreach.
        if (capability.route == ActionRoute.MANUAL_HANDOFF) {
            return ActionRefusal.MANUAL_STEP_ONLY
        }
        // A link-or-share route is a share, which is one of the acts the approval gate governs.
        // Refusing it here is what keeps "opens only" true rather than merely intended.
        if (capability.route == ActionRoute.APP_LINK_OR_SHARE) {
            return ActionRefusal.APPROVAL_REQUIRED
        }
        // Anything that is not a screen open is out of this division's declared scope.
        if (!DISPATCHABLE_ROUTES.contains(capability.route)) {
            return ActionRefusal.ROUTE_NOT_DISPATCHABLE
        }

        val decision = scopeEnforcer.authorize(packageName, actionId, cloudTransfer = cloudTransfer)
        if (decision is ScopeDecision.Refused) {
            return ActionRefusal.SCOPE_REFUSED
        }

        if (lease.stepRefusal(nowEpochMillis()) != null) {
            return ActionRefusal.LEASE_NOT_READY
        }

        // Last gate before dispatch: this division handles harmless opens only. A transfer is
        // refused here rather than performed without the approval gate that governs it.
        if (cloudTransfer) {
            return ActionRefusal.APPROVAL_REQUIRED
        }

        return null
    }
}
