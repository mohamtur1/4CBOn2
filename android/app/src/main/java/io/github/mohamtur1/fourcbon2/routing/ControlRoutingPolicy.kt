package io.github.mohamtur1.fourcbon2.routing

/**
 * Online/offline phone-control routing (Division 36).
 *
 * Routes control through available local, API, or model paths only. There is
 * no fake cloud planning, no locked-device bypass, and no silent alternate
 * provider: when no safe route exists, the decision is NONE.
 */
class ControlRoutingPolicy {
    enum class Route { LOCAL, API, MODEL, NONE }

    data class RoutingDecision(
        val route: Route,
        val reason: String,
    )

    fun decide(
        localAvailable: Boolean,
        apiAuthorized: Boolean,
        modelAvailable: Boolean,
        deviceLocked: Boolean,
    ): RoutingDecision {
        if (deviceLocked) {
            return RoutingDecision(Route.NONE, "Device is locked; no route is available.")
        }
        if (localAvailable) {
            return RoutingDecision(Route.LOCAL, "Local route is available.")
        }
        if (apiAuthorized) {
            return RoutingDecision(Route.API, "Authorized API route is available.")
        }
        if (modelAvailable) {
            return RoutingDecision(Route.MODEL, "Local model route is available.")
        }
        return RoutingDecision(Route.NONE, "No safe control route is available.")
    }

    /** Prompt-injection text never influences routing — routing is policy-driven. */
    fun routeForText(text: String, decision: RoutingDecision): RoutingDecision = decision
}
