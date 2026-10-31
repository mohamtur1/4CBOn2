package io.github.mohamtur1.fourcbon2.kaggle

/**
 * Kaggle metadata/status view (Division 23).
 *
 * The adapter reports the actual state of the Kaggle connection. Refreshing
 * competition metadata requires an authorized account; without a stored
 * credential the adapter reports [Status.UNAUTHORIZED] and never invents
 * competition data.
 */
class KaggleStatusAdapter {
    enum class Status {
        NOT_CONFIGURED,
        UNAUTHORIZED,
        AUTHORIZED,
        UNAVAILABLE,
        STALE,
    }

    data class StatusSnapshot(
        val status: Status,
        val lastSyncEpochMs: Long?,
        val detail: String,
    )

    /**
     * Probe the current state. A real refresh is only attempted when a
     * credential is present; otherwise the recorded blocker (HTTP 401 on the
     * fixed competition-list endpoint) is returned.
     */
    fun currentStatus(
        credentialPresent: Boolean,
        lastSyncEpochMs: Long? = null,
        clock: () -> Long = System::currentTimeMillis,
    ): StatusSnapshot {
        return if (!credentialPresent) {
            StatusSnapshot(
                status = Status.UNAUTHORIZED,
                lastSyncEpochMs = lastSyncEpochMs,
                detail = "Kaggle read requires an authorized account. The fixed competition-list endpoint returned HTTP 401 without a credential.",
            )
        } else {
            StatusSnapshot(
                status = Status.AUTHORIZED,
                lastSyncEpochMs = clock(),
                detail = "Kaggle metadata can be refreshed with the stored credential.",
            )
        }
    }
}
