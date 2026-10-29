package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionScopeEnforcerTest {
    private var now = 1_700_000_000_000L

    @Test
    fun permitsInScopeActionAndReportsTheResolvedRoute() {
        val enforcer = ActionScopeEnforcer(syntheticContract(), syntheticRegistry(), { now })

        val decision = enforcer.authorize("org.kaggle.app", "open")

        assertEquals(
            ScopeDecision.Permitted(
                scopeId = "scope-synthetic-1",
                packageName = "org.kaggle.app",
                actionId = "open",
                route = ActionRoute.APP_INTENT,
                sensitivity = ActionSensitivity.STANDARD,
                requiresOwnerConfirmation = false,
                cloudTransfer = false,
            ),
            decision,
        )
        assertEquals(ScopeState.ACTIVE, enforcer.state())
        assertEquals(20, enforcer.remainingActions())
    }

    @Test
    fun refusesTargetThatTheOwnerDidNotSelectForThisJob() {
        val enforcer = ActionScopeEnforcer(syntheticContract(), syntheticRegistry(), { now })

        assertEquals(
            ScopeDecision.Refused(
                "scope-synthetic-1",
                "org.colab.app",
                "open",
                ScopeRefusal.TARGET_NOT_IN_SCOPE,
            ),
            enforcer.authorize("org.colab.app", "open"),
        )
    }

    @Test
    fun refusesActionThatIsNotAllowedForAnInScopeTarget() {
        val enforcer = ActionScopeEnforcer(syntheticContract(), syntheticRegistry(), { now })

        assertEquals(
            ScopeRefusal.ACTION_NOT_IN_SCOPE,
            (enforcer.authorize("org.kaggle.app", "submit") as ScopeDecision.Refused).reason,
        )
    }

    @Test
    fun exclusionsWinOverAllowListsForBothTargetsAndActions() {
        val contract = syntheticContract(
            excludedPackages = setOf("org.bank.app"),
            excludedActionIds = setOf("open"),
        )
        val enforcer = ActionScopeEnforcer(contract, syntheticRegistry(), { now })

        assertEquals(
            ScopeRefusal.TARGET_EXCLUDED,
            (enforcer.authorize("org.bank.app", "pay-invoice") as ScopeDecision.Refused).reason,
        )
        assertEquals(
            ScopeRefusal.ACTION_EXCLUDED,
            (enforcer.authorize("org.kaggle.app", "open") as ScopeDecision.Refused).reason,
        )
    }

    @Test
    fun cloudTransferIsRefusedUnlessTheOwnerExplicitlyAllowedIt() {
        val denied = ActionScopeEnforcer(syntheticContract(), syntheticRegistry(), { now })
        assertEquals(
            ScopeRefusal.CLOUD_TRANSFER_NOT_PERMITTED,
            (denied.authorize("org.kaggle.app", "open", cloudTransfer = true) as ScopeDecision.Refused).reason,
        )

        val allowed = ActionScopeEnforcer(
            syntheticContract(cloudTransferAllowed = true),
            syntheticRegistry(),
            { now },
        )
        val permitted = allowed.authorize("org.kaggle.app", "open", cloudTransfer = true)
        assertTrue(permitted is ScopeDecision.Permitted)
        assertTrue((permitted as ScopeDecision.Permitted).cloudTransfer)

        // A local-only step still works on a contract that permits transfer.
        assertFalse(
            (allowed.authorize("org.kaggle.app", "open") as ScopeDecision.Permitted).cloudTransfer,
        )
    }

    @Test
    fun refusesEveryRequestAtAndAfterTheDerivedExpiry() {
        val enforcer = ActionScopeEnforcer(syntheticContract(), syntheticRegistry(), { now })

        now = 1_700_000_299_999L
        assertTrue(enforcer.authorize("org.kaggle.app", "open") is ScopeDecision.Permitted)
        assertEquals(ScopeState.ACTIVE, enforcer.state())

        now = 1_700_000_300_000L
        assertEquals(
            ScopeRefusal.SCOPE_EXPIRED,
            (enforcer.authorize("org.kaggle.app", "open") as ScopeDecision.Refused).reason,
        )
        assertEquals(ScopeState.EXPIRED, enforcer.state())
        assertThrows(IllegalStateException::class.java) {
            enforcer.recordCompletion("org.kaggle.app", "open")
        }
    }

    @Test
    fun surfacesTheRegistryReasonWhenNoCapabilityIsAvailable() {
        val registry = SelectedAppCapabilityRegistry(ActionRouteProbe.NONE)
        registry.select(syntheticApp("org.kaggle.app"))
        registry.declareAction("org.kaggle.app", openCapability())

        val enforcer = ActionScopeEnforcer(syntheticContract(), registry, { now })

        assertEquals(
            ScopeDecision.Refused(
                scopeId = "scope-synthetic-1",
                packageName = "org.kaggle.app",
                actionId = "open",
                reason = ScopeRefusal.CAPABILITY_UNAVAILABLE,
                detail = UnavailableReason.NO_SUPPORTED_ROUTE_AVAILABLE.name,
            ),
            enforcer.authorize("org.kaggle.app", "open"),
        )
    }

    @Test
    fun pausesOfASelectedAppStillFailClosedInsideAnActiveScope() {
        val registry = syntheticRegistry()
        registry.setOwnerEnabled("org.kaggle.app", ownerEnabled = false)
        val enforcer = ActionScopeEnforcer(syntheticContract(), registry, { now })

        assertEquals(
            ScopeRefusal.CAPABILITY_UNAVAILABLE,
            (enforcer.authorize("org.kaggle.app", "open") as ScopeDecision.Refused).reason,
        )
        assertEquals(
            UnavailableReason.APP_DISABLED_BY_OWNER.name,
            (enforcer.authorize("org.kaggle.app", "open") as ScopeDecision.Refused).detail,
        )
    }

    @Test
    fun actionBudgetIsConsumedOnlyByRecordedCompletions() {
        val contract = syntheticContract(limits = ScopeLimits(maxActions = 2))
        val enforcer = ActionScopeEnforcer(contract, syntheticRegistry(), { now })

        repeat(5) {
            assertTrue(enforcer.authorize("org.kaggle.app", "open") is ScopeDecision.Permitted)
        }
        assertEquals(0, enforcer.completedActionCount())
        assertEquals(2, enforcer.remainingActions())

        enforcer.recordCompletion("org.kaggle.app", "open")
        enforcer.recordCompletion("org.kaggle.app", "open")
        assertEquals(2, enforcer.completedActionCount())
        assertEquals(0, enforcer.remainingActions())
        assertEquals(ScopeState.EXHAUSTED, enforcer.state())

        assertEquals(
            ScopeRefusal.ACTION_LIMIT_REACHED,
            (enforcer.authorize("org.kaggle.app", "open") as ScopeDecision.Refused).reason,
        )
        assertThrows(IllegalStateException::class.java) {
            enforcer.recordCompletion("org.kaggle.app", "open")
        }
        assertEquals(2, enforcer.completedActionCount())
    }

    @Test
    fun ownerConfirmationIsRequiredForManualHandoffAndPerStepMode() {
        val perStep = ActionScopeEnforcer(
            syntheticContract(completionMode = CompletionMode.OWNER_CONFIRMS_EACH_STEP),
            syntheticRegistry(),
            { now },
        )
        assertTrue(
            (perStep.authorize("org.kaggle.app", "open") as ScopeDecision.Permitted).requiresOwnerConfirmation,
        )

        val manual = ActionScopeEnforcer(
            syntheticContract(completionMode = CompletionMode.AUTOMATIC_WITHIN_SCOPE),
            syntheticRegistry(),
            { now },
        )
        val manualDecision = manual.authorize("org.bank.app", "pay-invoice") as ScopeDecision.Permitted
        assertTrue(manualDecision.requiresOwnerAction)
        assertTrue("manual handoff always needs the owner, even in automatic mode", manualDecision.requiresOwnerConfirmation)
        assertEquals(ActionSensitivity.MANUAL_ONLY, manualDecision.sensitivity)

        val automatic = ActionScopeEnforcer(
            syntheticContract(completionMode = CompletionMode.AUTOMATIC_WITHIN_SCOPE),
            syntheticRegistry(),
            { now },
        )
        val automaticDecision = automatic.authorize("org.kaggle.app", "open") as ScopeDecision.Permitted
        assertFalse(automaticDecision.requiresOwnerConfirmation)
        assertFalse(automaticDecision.requiresOwnerAction)
    }

    private fun syntheticRegistry(): SelectedAppCapabilityRegistry {
        val reachable = setOf(
            "org.kaggle.app" to ActionRoute.APP_INTENT,
            "org.bank.app" to ActionRoute.MANUAL_HANDOFF,
        )
        val registry = SelectedAppCapabilityRegistry { packageName, _, route ->
            reachable.contains(packageName to route)
        }
        registry.select(syntheticApp("org.kaggle.app"))
        registry.select(syntheticApp("org.bank.app"))
        registry.declareAction("org.kaggle.app", openCapability())
        registry.declareAction(
            "org.bank.app",
            AppActionCapability(
                actionId = "pay-invoice",
                label = "Pay invoice",
                sensitivity = ActionSensitivity.MANUAL_ONLY,
                supportedRoutes = setOf(ActionRoute.MANUAL_HANDOFF),
            ),
        )
        return registry
    }

    private fun syntheticApp(packageName: String) = SelectedApp(
        packageName = packageName,
        displayLabel = "Synthetic $packageName",
        ownerSelectedAtEpochMillis = 1_700_000_000_000L,
    )

    private fun openCapability() = AppActionCapability(
        actionId = "open",
        label = "Open competition",
        sensitivity = ActionSensitivity.STANDARD,
        supportedRoutes = setOf(ActionRoute.APP_INTENT, ActionRoute.MANUAL_HANDOFF),
    )

    private fun syntheticContract(
        cloudTransferAllowed: Boolean = false,
        completionMode: CompletionMode = CompletionMode.OWNER_CONFIRMS_AT_END,
        limits: ScopeLimits = ScopeLimits(),
        excludedPackages: Set<String> = emptySet(),
        excludedActionIds: Set<String> = emptySet(),
    ) = ScopeContract(
        id = "scope-synthetic-1",
        goal = "Open one synthetic competition page.",
        targets = listOf(
            ScopeTarget("org.kaggle.app", setOf("open")),
            ScopeTarget("org.bank.app", setOf("pay-invoice")),
        ),
        allowedRecipients = setOf("owner-only"),
        allowedFileLabels = setOf("synthetic-report.pdf"),
        cloudTransferAllowed = cloudTransferAllowed,
        completionMode = completionMode,
        limits = limits,
        createdAtEpochMillis = 1_700_000_000_000L,
        excludedPackages = excludedPackages,
        excludedActionIds = excludedActionIds,
    )
}
