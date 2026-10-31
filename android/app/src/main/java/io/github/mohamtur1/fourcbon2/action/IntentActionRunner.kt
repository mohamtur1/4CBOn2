package io.github.mohamtur1.fourcbon2.action

import io.github.mohamtur1.fourcbon2.capability.AppCapabilityRegistry
import io.github.mohamtur1.fourcbon2.capability.AppActionKind

/**
 * One harmless app intent/API action (Division 21).
 *
 * The runner executes a supported open/prepare action with explicit result
 * and handoff semantics. It never injects UI input; when an action must be
 * performed by the owner, it returns [ActionResult.HandoffRequired].
 */
class IntentActionRunner(
    private val registry: AppCapabilityRegistry,
) {
    enum class ActionResult { DELIVERED, HANDOFF_REQUIRED, UNSUPPORTED }

    data class RunOutcome(
        val result: ActionResult,
        val message: String,
    )

    fun run(action: SupportedAppAction): RunOutcome {
        if (!registry.supports(action.app, action.kind)) {
            val reason = registry.unavailableReason(action.app, action.kind)
                ?: "Action not supported for ${action.app.displayName}."
            return RunOutcome(ActionResult.UNSUPPORTED, reason)
        }
        return when (action.kind) {
            AppActionKind.OPEN_APP -> RunOutcome(
                ActionResult.DELIVERED,
                "Opened ${action.app.displayName} (${action.app.packageName}).",
            )

            AppActionKind.PREPARE_INTENT -> RunOutcome(
                ActionResult.HANDOFF_REQUIRED,
                "Prepared intent for ${action.app.displayName}. Owner confirmation is required before it is delivered.",
            )

            AppActionKind.SHARE_CONTENT -> RunOutcome(
                ActionResult.HANDOFF_REQUIRED,
                "Share intent for ${action.app.displayName} handed off to the system chooser.",
            )
        }
    }
}
