package io.github.mohamtur1.fourcbon2

private val SCOPE_PACKAGE_NAME = Regex("(?:[A-Za-z][A-Za-z0-9_]*\\.)+[A-Za-z][A-Za-z0-9_]*")
private val SCOPE_ACTION_ID = Regex("[a-z][a-z0-9._-]{0,63}")
private val SCOPE_CONTRACT_ID = Regex("[A-Za-z0-9._:-]{1,64}")

private fun requireScopeText(value: String, fieldName: String, maxLength: Int) {
    require(value.isNotBlank()) { "$fieldName must not be blank." }
    require(value.length <= maxLength) { "$fieldName must be at most $maxLength characters." }
    require(value.none { character -> character == '\n' || character == '\r' || character == '\t' }) {
        "$fieldName must not contain control separators."
    }
}

/**
 * Hard upper bounds for one owner-started job. A contract cannot be created with a
 * looser budget than these, so a later caller cannot silently widen a scope.
 */
object ScopeBounds {
    /** Matches the owner-started proposal of at most twenty actions per job. */
    const val MAX_ACTIONS = 20

    /** Matches the owner-started proposal of at most five minutes per job. */
    const val MAX_DURATION_MILLIS = 5 * 60 * 1_000L

    const val DEFAULT_ACTIONS = MAX_ACTIONS
    const val DEFAULT_DURATION_MILLIS = MAX_DURATION_MILLIS
}

/** Who closes out the job once the bounded work is done. */
enum class CompletionMode {
    /** The owner approves every individual step. */
    OWNER_CONFIRMS_EACH_STEP,

    /** The owner approves the finished result once. */
    OWNER_CONFIRMS_AT_END,

    /** Bounded non-sensitive steps may complete without a per-step owner tap. */
    AUTOMATIC_WITHIN_SCOPE,
}

/** Bounded action/time budget for a single job. Loosening beyond [ScopeBounds] is rejected. */
data class ScopeLimits(
    val maxActions: Int = ScopeBounds.DEFAULT_ACTIONS,
    val durationMillis: Long = ScopeBounds.DEFAULT_DURATION_MILLIS,
) {
    init {
        require(maxActions in 1..ScopeBounds.MAX_ACTIONS) {
            "maxActions must be between 1 and ${ScopeBounds.MAX_ACTIONS}."
        }
        require(durationMillis in 1..ScopeBounds.MAX_DURATION_MILLIS) {
            "durationMillis must be between 1 and ${ScopeBounds.MAX_DURATION_MILLIS}."
        }
    }
}

/**
 * One owner-selected app and the exact actions the owner allowed for it in this job.
 * Being listed here is not permission to use any other action of that app.
 */
data class ScopeTarget(
    val packageName: String,
    val allowedActionIds: Set<String>,
) {
    init {
        require(SCOPE_PACKAGE_NAME.matches(packageName)) {
            "packageName must be a dotted Android package name such as com.example.app."
        }
        require(allowedActionIds.isNotEmpty()) { "At least one allowed action must be named." }
        allowedActionIds.forEach { actionId ->
            require(SCOPE_ACTION_ID.matches(actionId)) {
                "allowedActionIds must contain 1-64 lowercase letters, digits, dot, underscore, or hyphen, starting with a letter."
            }
        }
    }

    fun allows(actionId: String): Boolean = allowedActionIds.contains(actionId)
}

/**
 * A local, self-describing promise sheet for one job: what it may do, to whom, with
 * which files, whether anything may leave the device, how it ends, and when it expires.
 *
 * Cloud transfer is **off unless the owner explicitly turns it on**, and target or
 * action exclusions always win over an allow-list entry.
 */
data class ScopeContract(
    val id: String,
    val goal: String,
    val targets: List<ScopeTarget>,
    val allowedRecipients: Set<String> = emptySet(),
    val allowedFileLabels: Set<String> = emptySet(),
    val cloudTransferAllowed: Boolean = false,
    val completionMode: CompletionMode = CompletionMode.OWNER_CONFIRMS_AT_END,
    val limits: ScopeLimits = ScopeLimits(),
    val createdAtEpochMillis: Long,
    val excludedPackages: Set<String> = emptySet(),
    val excludedActionIds: Set<String> = emptySet(),
) {
    init {
        require(SCOPE_CONTRACT_ID.matches(id)) {
            "id must be 1-64 characters using letters, digits, dot, underscore, colon, or hyphen."
        }
        requireScopeText(goal, "goal", 1_000)
        require(targets.isNotEmpty()) { "At least one selected target is required." }
        val duplicates = targets.groupBy { target -> target.packageName }.filterValues { group -> group.size > 1 }
        require(duplicates.isEmpty()) { "Duplicate target packages are not allowed: ${duplicates.keys.sorted()}." }
        allowedRecipients.forEach { recipient -> requireScopeText(recipient, "allowedRecipients entry", 80) }
        allowedFileLabels.forEach { label -> requireScopeText(label, "allowedFileLabels entry", 80) }
        excludedPackages.forEach { packageName ->
            require(SCOPE_PACKAGE_NAME.matches(packageName)) {
                "excludedPackages must contain dotted Android package names."
            }
        }
        excludedActionIds.forEach { actionId ->
            require(SCOPE_ACTION_ID.matches(actionId)) {
                "excludedActionIds must contain 1-64 lowercase letters, digits, dot, underscore, or hyphen, starting with a letter."
            }
        }
        require(createdAtEpochMillis > 0) { "createdAtEpochMillis must be positive." }
    }

    /** Absolute expiry derived from the owner-started creation time and the bounded duration. */
    val expiresAtEpochMillis: Long get() = createdAtEpochMillis + limits.durationMillis

    fun targetFor(packageName: String): ScopeTarget? =
        targets.firstOrNull { target -> target.packageName == packageName }

    fun isExpiredAt(nowEpochMillis: Long): Boolean = nowEpochMillis >= expiresAtEpochMillis

    fun remainingMillisAt(nowEpochMillis: Long): Long =
        (expiresAtEpochMillis - nowEpochMillis).coerceAtLeast(0L)
}

/** Why a requested action was refused. Every refusal names exactly one cause. */
enum class ScopeRefusal {
    SCOPE_EXPIRED,
    TARGET_NOT_IN_SCOPE,
    ACTION_NOT_IN_SCOPE,
    TARGET_EXCLUDED,
    ACTION_EXCLUDED,
    CLOUD_TRANSFER_NOT_PERMITTED,
    ACTION_LIMIT_REACHED,
    CAPABILITY_UNAVAILABLE,
}

/** Lifecycle of one bounded job. */
enum class ScopeState {
    ACTIVE,
    EXPIRED,
    EXHAUSTED,
}

/**
 * The outcome of one bounded request: either a permitted concrete route or a refusal
 * with one explicit cause. There is no implicit "maybe" and no silent escalation.
 */
sealed interface ScopeDecision {
    val scopeId: String
    val packageName: String
    val actionId: String

    data class Permitted(
        override val scopeId: String,
        override val packageName: String,
        override val actionId: String,
        val route: ActionRoute,
        val sensitivity: ActionSensitivity,
        val requiresOwnerConfirmation: Boolean,
        val cloudTransfer: Boolean,
    ) : ScopeDecision {
        /** The owner performs the final step, so the agent cannot self-approve it. */
        val requiresOwnerAction: Boolean get() = route == ActionRoute.MANUAL_HANDOFF
    }

    data class Refused(
        override val scopeId: String,
        override val packageName: String,
        override val actionId: String,
        val reason: ScopeRefusal,
        val detail: String? = null,
    ) : ScopeDecision
}
