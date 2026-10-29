package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SelectedAppCapabilityTest {
    @Test
    fun selectedAppAcceptsOnlyDottedAndroidPackageNames() {
        val app = SelectedApp(
            packageName = "org.kaggle.app",
            displayLabel = "Kaggle (synthetic)",
            ownerSelectedAtEpochMillis = 1_700_000_000_000L,
        )

        assertEquals("org.kaggle.app", app.packageName)
        assertEquals("Kaggle (synthetic)", app.displayLabel)

        listOf(
            "",
            "single",
            ".leading.dot",
            "io..github",
            "io.github.mohamtur1.fourcbon2 ",
            "1invalid.example",
            "io.github.4cbon2",
        ).forEach { invalidPackageName ->
            assertThrows(IllegalArgumentException::class.java) {
                SelectedApp(
                    packageName = invalidPackageName,
                    displayLabel = "Synthetic app",
                    ownerSelectedAtEpochMillis = 1_700_000_000_000L,
                )
            }
        }
    }

    @Test
    fun selectedAppRejectsBlankOrControlLabelsAndNonPositiveSelectionTimes() {
        assertThrows(IllegalArgumentException::class.java) {
            SelectedApp("org.kaggle.app", "   ", 1_700_000_000_000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SelectedApp("org.kaggle.app", "line\nbreak", 1_700_000_000_000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SelectedApp("org.kaggle.app", "a".repeat(81), 1_700_000_000_000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SelectedApp("org.kaggle.app", "Kaggle", 0L)
        }
    }

    @Test
    fun actionCapabilityRejectsMalformedIdentifiersLabelsAndEmptyRoutes() {
        assertThrows(IllegalArgumentException::class.java) {
            AppActionCapability("Open App", "Open", ActionSensitivity.STANDARD, setOf(ActionRoute.APP_INTENT))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppActionCapability("open", "", ActionSensitivity.STANDARD, setOf(ActionRoute.APP_INTENT))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppActionCapability("open", "Open", ActionSensitivity.STANDARD, emptySet())
        }

        val capability = AppActionCapability(
            actionId = "open-compose",
            label = "Open draft",
            sensitivity = ActionSensitivity.STANDARD,
            supportedRoutes = setOf(ActionRoute.APP_INTENT, ActionRoute.AUTHORIZED_API),
        )

        assertEquals("open-compose", capability.actionId)
        assertEquals(ActionRoute.AUTHORIZED_API, capability.leastPowerfulRoute())
    }

    @Test
    fun manualOnlyActionsMustDeclareExactlyManualHandoff() {
        assertThrows(IllegalArgumentException::class.java) {
            AppActionCapability(
                actionId = "pay-invoice",
                label = "Pay invoice",
                sensitivity = ActionSensitivity.MANUAL_ONLY,
                supportedRoutes = setOf(ActionRoute.AUTHORIZED_API, ActionRoute.MANUAL_HANDOFF),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppActionCapability(
                actionId = "pay-invoice",
                label = "Pay invoice",
                sensitivity = ActionSensitivity.MANUAL_ONLY,
                supportedRoutes = setOf(ActionRoute.APP_INTENT),
            )
        }

        val manualOnly = AppActionCapability(
            actionId = "pay-invoice",
            label = "Pay invoice",
            sensitivity = ActionSensitivity.MANUAL_ONLY,
            supportedRoutes = setOf(ActionRoute.MANUAL_HANDOFF),
        )

        assertEquals(ActionRoute.MANUAL_HANDOFF, manualOnly.leastPowerfulRoute())
    }

    @Test
    fun routeOrderingRunsFromLeastToMostPowerful() {
        val expectedOrder = listOf(
            ActionRoute.AUTHORIZED_API,
            ActionRoute.APP_INTENT,
            ActionRoute.APP_LINK_OR_SHARE,
            ActionRoute.FIXED_ROUTINE,
            ActionRoute.MANUAL_HANDOFF,
        )

        assertEquals(expectedOrder, ActionRoute.entries.toList())
        assertEquals(expectedOrder.sortedBy { it.ordinal }, expectedOrder)
        assertEquals(
            ActionRoute.APP_LINK_OR_SHARE,
            ActionRoute.leastPowerful(
                setOf(ActionRoute.MANUAL_HANDOFF, ActionRoute.FIXED_ROUTINE, ActionRoute.APP_LINK_OR_SHARE),
            ),
        )
        assertNull(ActionRoute.leastPowerful(emptySet()))
    }
}
