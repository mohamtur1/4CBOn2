package io.github.mohamtur1.fourcbon2

private val ANDROID_PACKAGE_NAME = Regex("(?:[A-Za-z][A-Za-z0-9_]*\\.)+[A-Za-z][A-Za-z0-9_]*")
private val CAPABILITY_ACTION_ID = Regex("[a-z][a-z0-9._-]{0,63}")

private fun validateCapabilityText(value: String, fieldName: String, maxLength: Int) {
    require(value.isNotBlank()) { "$fieldName must not be blank." }
    require(value.length <= maxLength) { "$fieldName must be at most $maxLength characters." }
    require(value.none { character -> character == '\n' || character == '\r' || character == '\t' }) {
        "$fieldName must not contain control separators."
    }
}

/**
 * How a selected app could carry out one action, ordered from least to most
 * powerful. The ordinal order is a safety contract, not a display preference:
 * a resolved request must use the earliest (least powerful) available route.
 *
 * - [AUTHORIZED_API] — the app exposes a documented, owner-authorized API.
 * - [APP_INTENT] — a supported Android intent that the app itself answers.
 * - [APP_LINK_OR_SHARE] — a verified app link or the system share surface.
 * - [FIXED_ROUTINE] — a bounded, human-defined routine the owner previously approved.
 * - [MANUAL_HANDOFF] — the owner performs the step; 4CBOn2 only prepares it.
 */
enum class ActionRoute {
    AUTHORIZED_API,
    APP_INTENT,
    APP_LINK_OR_SHARE,
    FIXED_ROUTINE,
    MANUAL_HANDOFF,
    ;

    companion object {
        /** Returns the least powerful route in [routes], or `null` when none is given. */
        fun leastPowerful(routes: Collection<ActionRoute>): ActionRoute? = routes.minByOrNull { it.ordinal }
    }
}

enum class ActionSensitivity {
    STANDARD,
    MANUAL_ONLY,
}

/**
 * An app the owner explicitly named. This is not a package inventory: an
 * instance exists only because the owner selected that package on purpose.
 * It carries no account, credential, device identifier, or installed-app list.
 */
data class SelectedApp(
    val packageName: String,
    val displayLabel: String,
    val ownerSelectedAtEpochMillis: Long,
) {
    init {
        require(ANDROID_PACKAGE_NAME.matches(packageName)) {
            "packageName must be a dotted Android package name such as com.example.app."
        }
        validateCapabilityText(displayLabel, "displayLabel", 80)
        require(ownerSelectedAtEpochMillis > 0) { "ownerSelectedAtEpochMillis must be positive." }
    }
}

/**
 * One owner-visible action for one selected app, with the routes its owner is
 * willing to allow. Banking, payment, password, one-time-code, biometric,
 * permission, and security-setting actions must use [ActionSensitivity.MANUAL_ONLY].
 */
data class AppActionCapability(
    val actionId: String,
    val label: String,
    val sensitivity: ActionSensitivity,
    val supportedRoutes: Set<ActionRoute>,
) {
    init {
        require(CAPABILITY_ACTION_ID.matches(actionId)) {
            "actionId must be 1-64 lowercase letters, digits, dot, underscore, or hyphen, starting with a letter."
        }
        validateCapabilityText(label, "label", 100)
        require(supportedRoutes.isNotEmpty()) { "At least one supported route must be declared." }
        if (sensitivity == ActionSensitivity.MANUAL_ONLY) {
            require(supportedRoutes == setOf(ActionRoute.MANUAL_HANDOFF)) {
                "Manual-only actions must declare exactly the MANUAL_HANDOFF route."
            }
        }
    }

    /** The least powerful declared route; manual-only actions can only return [ActionRoute.MANUAL_HANDOFF]. */
    fun leastPowerfulRoute(): ActionRoute =
        checkNotNull(ActionRoute.leastPowerful(supportedRoutes)) { "No supported route is declared." }
}

enum class UnavailableReason {
    APP_NOT_SELECTED,
    APP_DISABLED_BY_OWNER,
    ACTION_NOT_DECLARED,
    NO_SUPPORTED_ROUTE_AVAILABLE,
}

/**
 * The explicit outcome of asking whether one selected app can perform one action.
 * There is no ambiguous "maybe": the registry returns either one concrete route or
 * one concrete reason it is unavailable.
 */
sealed interface CapabilityDecision {
    val packageName: String
    val actionId: String

    data class Supported(
        override val packageName: String,
        override val actionId: String,
        val route: ActionRoute,
        val sensitivity: ActionSensitivity,
    ) : CapabilityDecision {
        /** A manual handoff always leaves the final step to the owner. */
        val requiresOwnerAction: Boolean get() = route == ActionRoute.MANUAL_HANDOFF
    }

    data class Unavailable(
        override val packageName: String,
        override val actionId: String,
        val reason: UnavailableReason,
    ) : CapabilityDecision
}
