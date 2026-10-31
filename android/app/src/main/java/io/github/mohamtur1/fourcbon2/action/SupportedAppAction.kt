package io.github.mohamtur1.fourcbon2.action

import io.github.mohamtur1.fourcbon2.capability.AppActionKind
import io.github.mohamtur1.fourcbon2.capability.SelectedApp

/**
 * One supported app action (Division 21).
 *
 * Only open/prepare/share actions are supported. There is no unrestricted
 * UI input: an action never injects taps, text, or key events.
 */
data class SupportedAppAction(
    val app: SelectedApp,
    val kind: AppActionKind,
    val description: String,
    val requiresApproval: Boolean,
) {
    init {
        require(description.isNotBlank()) { "Action description is required." }
    }
}
