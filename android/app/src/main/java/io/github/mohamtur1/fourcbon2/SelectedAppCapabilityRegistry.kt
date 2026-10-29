package io.github.mohamtur1.fourcbon2

/**
 * Narrow discovery boundary for one selected app.
 *
 * Implementations may answer only the exact app/action/route question the caller
 * names. They must never enumerate installed packages, list other apps' activities,
 * read notifications, capture the screen, or inspect another app's data.
 * The default implementation treats every route as unreachable until it is proven
 * available by an explicitly wired probe.
 */
fun interface ActionRouteProbe {
    fun isAvailable(packageName: String, actionId: String, route: ActionRoute): Boolean

    companion object {
        /** Conservative default: nothing is reachable until a real route is proven. */
        val NONE: ActionRouteProbe = ActionRouteProbe { _, _, _ -> false }
    }
}

/**
 * An owner-controlled, in-memory registry of selected apps and their declared,
 * action-level capabilities.
 *
 * It is deliberately **not** a package inventory: an app exists here only because
 * the owner named it explicitly. Persistence and UI wiring are deferred, so this
 * division adds no schema version, no database migration, and no background work.
 *
 * Resolution is fail-closed. A missing app, an owner-disabled app, an undeclared
 * action, a probe failure, or a probe that reports nothing reachable all produce a
 * [CapabilityDecision.Unavailable] with an explicit reason; the registry never
 * guesses a route and never upgrades to a more powerful one.
 */
class SelectedAppCapabilityRegistry(
    private val routeAvailabilityProbe: ActionRouteProbe = ActionRouteProbe.NONE,
) {
    private data class Entry(
        val app: SelectedApp,
        var ownerEnabled: Boolean,
        val actions: MutableMap<String, AppActionCapability> = linkedMapOf(),
    )

    private val entries = linkedMapOf<String, Entry>()

    /** Records an owner-selected app. Selecting the same package twice is rejected. */
    @Synchronized
    fun select(app: SelectedApp, ownerEnabled: Boolean = true) {
        check(!entries.containsKey(app.packageName)) {
            "App ${app.packageName} is already selected; use setOwnerEnabled or declareAction instead."
        }
        entries[app.packageName] = Entry(app = app, ownerEnabled = ownerEnabled)
    }

    @Synchronized
    fun deselect(packageName: String): Boolean = entries.remove(packageName) != null

    /** Owner pause/resume for one selected app; disabled apps are never probed or resolved. */
    @Synchronized
    fun setOwnerEnabled(packageName: String, ownerEnabled: Boolean) {
        requireEntry(packageName).ownerEnabled = ownerEnabled
    }

    @Synchronized
    fun declareAction(packageName: String, capability: AppActionCapability) {
        val entry = requireEntry(packageName)
        check(!entry.actions.containsKey(capability.actionId)) {
            "Action ${capability.actionId} is already declared for $packageName."
        }
        entry.actions[capability.actionId] = capability
    }

    @Synchronized
    fun isSelected(packageName: String): Boolean = entries.containsKey(packageName)

    /** Deterministic snapshot for future persistence or display; sorted by package name. */
    @Synchronized
    fun selectedApps(): List<SelectedAppSummary> =
        entries.values
            .sortedBy { entry -> entry.app.packageName }
            .map { entry ->
                SelectedAppSummary(
                    app = entry.app,
                    ownerEnabled = entry.ownerEnabled,
                    declaredActionIds = entry.actions.keys.sorted(),
                )
            }

    @Synchronized
    fun declaredActions(packageName: String): List<AppActionCapability> =
        requireEntry(packageName).actions.values.sortedBy { it.actionId }

    /**
     * Resolves the least powerful route that is both declared by the owner and
     * currently reachable. The probe is consulted only for this exact app and
     * action, and only after the app is selected, owner-enabled, and the action
     * is declared — so a paused app causes no discovery at all.
     */
    @Synchronized
    fun resolve(packageName: String, actionId: String): CapabilityDecision {
        val entry = entries[packageName]
            ?: return unavailable(packageName, actionId, UnavailableReason.APP_NOT_SELECTED)
        if (!entry.ownerEnabled) {
            return unavailable(packageName, actionId, UnavailableReason.APP_DISABLED_BY_OWNER)
        }
        val capability = entry.actions[actionId]
            ?: return unavailable(packageName, actionId, UnavailableReason.ACTION_NOT_DECLARED)

        val reachable = capability.supportedRoutes.filter { route -> isProbeReachable(packageName, actionId, route) }
        val route = ActionRoute.leastPowerful(reachable)
            ?: return unavailable(packageName, actionId, UnavailableReason.NO_SUPPORTED_ROUTE_AVAILABLE)

        return CapabilityDecision.Supported(
            packageName = packageName,
            actionId = actionId,
            route = route,
            sensitivity = capability.sensitivity,
        )
    }

    @Synchronized
    fun clear() = entries.clear()

    /**
     * A probe that throws is treated as unreachable. Discovery failures must never
     * be interpreted as permission to try a stronger route.
     */
    private fun isProbeReachable(packageName: String, actionId: String, route: ActionRoute): Boolean =
        try {
            routeAvailabilityProbe.isAvailable(packageName, actionId, route)
        } catch (error: Exception) {
            false
        }

    private fun unavailable(
        packageName: String,
        actionId: String,
        reason: UnavailableReason,
    ): CapabilityDecision.Unavailable =
        CapabilityDecision.Unavailable(packageName = packageName, actionId = actionId, reason = reason)

    private fun requireEntry(packageName: String): Entry = entries[packageName]
        ?: throw IllegalArgumentException("App $packageName is not owner-selected.")

    data class SelectedAppSummary(
        val app: SelectedApp,
        val ownerEnabled: Boolean,
        val declaredActionIds: List<String>,
    )
}
