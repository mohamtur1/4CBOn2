package io.github.mohamtur1.fourcbon2.capability

/** Action-level support for a selected app. */
enum class AppActionKind {
    /** Launch the app to a supported surface (harmless, owner-visible). */
    OPEN_APP,

    /** Prepare an intent/share without executing a consequential step. */
    PREPARE_INTENT,

    /** Hand off content to the app via a share intent. */
    SHARE_CONTENT,
}

/**
 * One supported action for a selected app. Unavailable actions are recorded
 * with an explicit reason instead of being hidden or claimed as supported.
 */
data class AppCapability(
    val app: SelectedApp,
    val action: AppActionKind,
    val supported: Boolean,
    val reason: String,
)
