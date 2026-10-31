package io.github.mohamtur1.fourcbon2.scope

/** Bounded scope limits for one control session. */
data class ScopeLimits(
    val allowedDestinations: List<String>,
    val maxActions: Int,
    val expiryEpochMs: Long,
    val allowCloudUpload: Boolean,
)

/**
 * On-device scope contract (Division 13).
 *
 * Every action is checked against the active scope: unselected targets are
 * denied, expiry is enforced, action counts are capped, and unauthorized
 * cloud transfer is rejected.
 */
class ScopeContract(
    private val scope: ScopeLimits,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var actionsUsed: Int = 0
        private set

    val isExpired: Boolean
        get() = clock() > scope.expiryEpochMs

    fun canPerform(
        destination: String,
        action: String,
        isCloudTransfer: Boolean = false,
    ): Boolean {
        if (isExpired) return false
        if (destination !in scope.allowedDestinations) return false
        if (isCloudTransfer && !scope.allowCloudUpload) return false
        if (actionsUsed >= scope.maxActions) return false
        return true
    }

    fun beginAction(
        destination: String,
        action: String,
        isCloudTransfer: Boolean = false,
    ): Boolean {
        if (!canPerform(destination, action, isCloudTransfer)) return false
        actionsUsed++
        return true
    }
}
