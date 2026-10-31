package io.github.mohamtur1.fourcbon2.capability

/**
 * Selected-app capability registry (Division 12).
 *
 * Only owner-selected apps receive any capability. An unselected app receives
 * no input and no observation grant. Support is recorded per action with an
 * explicit unavailable state — never a blanket inventory.
 */
class AppCapabilityRegistry {
    private val capabilities = mutableListOf<AppCapability>()

    fun register(capability: AppCapability) {
        val existing = capabilities.indexOfFirst {
            it.app == capability.app && it.action == capability.action
        }
        if (existing >= 0) {
            capabilities[existing] = capability
        } else {
            capabilities.add(capability)
        }
    }

    fun isSelected(app: SelectedApp): Boolean =
        capabilities.any { it.app == app }

    fun supports(app: SelectedApp, action: AppActionKind): Boolean {
        require(isSelected(app)) { "App is not selected: ${app.packageName}" }
        return capabilities.any { it.app == app && it.action == action && it.supported }
    }

    fun unavailableReason(app: SelectedApp, action: AppActionKind): String? {
        require(isSelected(app)) { "App is not selected: ${app.packageName}" }
        return capabilities.firstOrNull { it.app == app && it.action == action && !it.supported }?.reason
    }

    fun capabilitiesFor(app: SelectedApp): List<AppCapability> =
        capabilities.filter { it.app == app }

    fun all(): List<AppCapability> = capabilities.toList()
}
