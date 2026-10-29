package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedAppCapabilityRegistryTest {
    @Test
    fun resolveReportsNotSelectedWithoutProbing() {
        val probe = RecordingProbe()
        val registry = SelectedAppCapabilityRegistry(probe)

        val decision = registry.resolve("org.unselected.app", "open")

        assertEquals(
            CapabilityDecision.Unavailable("org.unselected.app", "open", UnavailableReason.APP_NOT_SELECTED),
            decision,
        )
        assertTrue(probe.calls.isEmpty())
    }

    @Test
    fun ownerDisabledAppStopsResolutionBeforeProbing() {
        val probe = RecordingProbe(reachable = setOf("org.kaggle.app" to ActionRoute.APP_INTENT))
        val registry = SelectedAppCapabilityRegistry(probe)
        registry.select(syntheticApp(), ownerEnabled = false)
        registry.declareAction("org.kaggle.app", openCapability())

        val paused = registry.resolve("org.kaggle.app", "open")

        assertEquals(
            CapabilityDecision.Unavailable(
                "org.kaggle.app",
                "open",
                UnavailableReason.APP_DISABLED_BY_OWNER,
            ),
            paused,
        )
        assertTrue(probe.calls.isEmpty())

        registry.setOwnerEnabled("org.kaggle.app", ownerEnabled = true)

        assertEquals(
            CapabilityDecision.Supported(
                packageName = "org.kaggle.app",
                actionId = "open",
                route = ActionRoute.APP_INTENT,
                sensitivity = ActionSensitivity.STANDARD,
            ),
            registry.resolve("org.kaggle.app", "open"),
        )
    }

    @Test
    fun undeclaredActionIsReportedAndSendsNoProbe() {
        val probe = RecordingProbe(reachable = setOf("org.kaggle.app" to ActionRoute.APP_INTENT))
        val registry = SelectedAppCapabilityRegistry(probe)
        registry.select(syntheticApp())

        val decision = registry.resolve("org.kaggle.app", "submit")

        assertEquals(
            CapabilityDecision.Unavailable(
                "org.kaggle.app",
                "submit",
                UnavailableReason.ACTION_NOT_DECLARED,
            ),
            decision,
        )
        assertTrue(probe.calls.isEmpty())
    }

    @Test
    fun resolveChoosesTheLeastPowerfulDeclaredAndReachableRoute() {
        val probe = RecordingProbe(
            reachable = setOf(
                "org.kaggle.app" to ActionRoute.APP_INTENT,
                "org.kaggle.app" to ActionRoute.MANUAL_HANDOFF,
            ),
        )
        val registry = SelectedAppCapabilityRegistry(probe)
        registry.select(syntheticApp())
        registry.declareAction(
            "org.kaggle.app",
            AppActionCapability(
                actionId = "open",
                label = "Open competition",
                sensitivity = ActionSensitivity.STANDARD,
                supportedRoutes = setOf(
                    ActionRoute.AUTHORIZED_API,
                    ActionRoute.APP_INTENT,
                    ActionRoute.MANUAL_HANDOFF,
                ),
            ),
        )

        val decision = registry.resolve("org.kaggle.app", "open")

        assertEquals(
            CapabilityDecision.Supported(
                packageName = "org.kaggle.app",
                actionId = "open",
                route = ActionRoute.APP_INTENT,
                sensitivity = ActionSensitivity.STANDARD,
            ),
            decision,
        )
        assertFalse((decision as CapabilityDecision.Supported).requiresOwnerAction)
    }

    @Test
    fun resolveFallsBackToManualHandoffAndMarksItOwnerAction() {
        val probe = RecordingProbe(reachable = setOf("org.bank.app" to ActionRoute.MANUAL_HANDOFF))
        val registry = SelectedAppCapabilityRegistry(probe)
        registry.select(
            SelectedApp(
                packageName = "org.bank.app",
                displayLabel = "Bank (synthetic)",
                ownerSelectedAtEpochMillis = 1_700_000_000_000L,
            ),
        )
        registry.declareAction(
            "org.bank.app",
            AppActionCapability(
                actionId = "pay-invoice",
                label = "Pay invoice",
                sensitivity = ActionSensitivity.MANUAL_ONLY,
                supportedRoutes = setOf(ActionRoute.MANUAL_HANDOFF),
            ),
        )

        val decision = registry.resolve("org.bank.app", "pay-invoice")

        assertEquals(
            CapabilityDecision.Supported(
                packageName = "org.bank.app",
                actionId = "pay-invoice",
                route = ActionRoute.MANUAL_HANDOFF,
                sensitivity = ActionSensitivity.MANUAL_ONLY,
            ),
            decision,
        )
        assertTrue((decision as CapabilityDecision.Supported).requiresOwnerAction)
    }

    @Test
    fun resolveReportsUnavailableWhenNoDeclaredRouteIsReachable() {
        val registry = SelectedAppCapabilityRegistry(RecordingProbe())
        registry.select(syntheticApp())
        registry.declareAction("org.kaggle.app", openCapability())

        assertEquals(
            CapabilityDecision.Unavailable(
                "org.kaggle.app",
                "open",
                UnavailableReason.NO_SUPPORTED_ROUTE_AVAILABLE,
            ),
            registry.resolve("org.kaggle.app", "open"),
        )
    }

    @Test
    fun probeFailureIsTreatedAsUnavailableRatherThanPermission() {
        val registry = SelectedAppCapabilityRegistry(FailingProbe())
        registry.select(syntheticApp())
        registry.declareAction("org.kaggle.app", openCapability())

        assertEquals(
            CapabilityDecision.Unavailable(
                "org.kaggle.app",
                "open",
                UnavailableReason.NO_SUPPORTED_ROUTE_AVAILABLE,
            ),
            registry.resolve("org.kaggle.app", "open"),
        )
    }

    @Test
    fun narrowDiscoveryOnlyProbesTheRequestedAppActionAndDeclaredRoutes() {
        val probe = RecordingProbe(reachable = setOf("org.kaggle.app" to ActionRoute.APP_INTENT))
        val registry = SelectedAppCapabilityRegistry(probe)
        registry.select(syntheticApp())
        registry.select(
            SelectedApp(
                packageName = "org.colab.app",
                displayLabel = "Colab (synthetic)",
                ownerSelectedAtEpochMillis = 1_700_000_000_500L,
            ),
        )
        registry.declareAction("org.kaggle.app", openCapability())
        registry.declareAction("org.colab.app", openCapability())

        registry.resolve("org.kaggle.app", "open")

        assertTrue(probe.calls.isNotEmpty())
        assertTrue(probe.calls.all { call -> call.packageName == "org.kaggle.app" })
        assertTrue(probe.calls.all { call -> call.actionId == "open" })
        assertTrue(probe.calls.all { call -> call.route in openCapability().supportedRoutes })
        assertEquals(
            openCapability().supportedRoutes,
            probe.calls.map { call -> call.route }.toSet(),
        )
    }

    @Test
    fun registryRejectsDuplicateSelectionAndWritesForUnselectedApps() {
        val registry = SelectedAppCapabilityRegistry(RecordingProbe())
        registry.select(syntheticApp())

        assertThrows(IllegalStateException::class.java) {
            registry.select(syntheticApp())
        }
        assertThrows(IllegalStateException::class.java) {
            registry.declareAction("org.kaggle.app", openCapability())
            registry.declareAction("org.kaggle.app", openCapability())
        }
        assertThrows(IllegalArgumentException::class.java) {
            registry.declareAction("org.colab.app", openCapability())
        }
        assertThrows(IllegalArgumentException::class.java) {
            registry.setOwnerEnabled("org.colab.app", ownerEnabled = false)
        }
        assertFalse(registry.isSelected("org.colab.app"))
        assertTrue(registry.deselect("org.kaggle.app"))
        assertFalse(registry.deselect("org.kaggle.app"))
    }

    @Test
    fun selectedAppSnapshotIsSortedAndTracksOwnerState() {
        val registry = SelectedAppCapabilityRegistry(RecordingProbe())
        registry.select(
            SelectedApp(
                packageName = "org.zebra.app",
                displayLabel = "Zebra (synthetic)",
                ownerSelectedAtEpochMillis = 1_700_000_000_000L,
            ),
        )
        registry.select(syntheticApp())
        registry.declareAction("org.kaggle.app", openCapability())
        registry.declareAction(
            "org.kaggle.app",
            AppActionCapability(
                actionId = "browse",
                label = "Browse competitions",
                sensitivity = ActionSensitivity.STANDARD,
                supportedRoutes = setOf(ActionRoute.APP_LINK_OR_SHARE),
            ),
        )

        assertEquals(
            listOf("org.kaggle.app", "org.zebra.app"),
            registry.selectedApps().map { summary -> summary.app.packageName },
        )
        assertEquals(
            listOf("browse", "open"),
            registry.selectedApps().first().declaredActionIds,
        )
        assertTrue(registry.selectedApps().first().ownerEnabled)

        registry.setOwnerEnabled("org.kaggle.app", ownerEnabled = false)

        assertFalse(registry.selectedApps().first().ownerEnabled)
        assertEquals(
            listOf("browse", "open"),
            registry.declaredActions("org.kaggle.app").map { capability -> capability.actionId },
        )
    }

    private fun syntheticApp() = SelectedApp(
        packageName = "org.kaggle.app",
        displayLabel = "Kaggle (synthetic)",
        ownerSelectedAtEpochMillis = 1_700_000_000_000L,
    )

    private fun openCapability() = AppActionCapability(
        actionId = "open",
        label = "Open competition",
        sensitivity = ActionSensitivity.STANDARD,
        supportedRoutes = setOf(ActionRoute.AUTHORIZED_API, ActionRoute.APP_INTENT),
    )

    private data class ProbeCall(
        val packageName: String,
        val actionId: String,
        val route: ActionRoute,
    )

    private class RecordingProbe(
        private val reachable: Set<Pair<String, ActionRoute>> = emptySet(),
    ) : ActionRouteProbe {
        val calls = mutableListOf<ProbeCall>()

        override fun isAvailable(packageName: String, actionId: String, route: ActionRoute): Boolean {
            calls += ProbeCall(packageName = packageName, actionId = actionId, route = route)
            return reachable.contains(packageName to route)
        }
    }

    private class FailingProbe : ActionRouteProbe {
        override fun isAvailable(packageName: String, actionId: String, route: ActionRoute): Boolean =
            throw IllegalStateException("Synthetic discovery failure.")
    }
}
