package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedJobControllerTest {
    private var now = CREATED_AT

    @Test
    fun permitsAnInScopeStepWhileTheLeaseIsReady() {
        val controller = syntheticController()

        val decision = controller.authorizeStep("org.kaggle.app", "open")

        assertTrue(decision is JobStepDecision.Permitted)
        decision as JobStepDecision.Permitted
        assertEquals(LeaseBounds.INITIAL_EPOCH, decision.leaseEpoch)
        assertEquals(ActionRoute.APP_INTENT, decision.scope.route)
        assertEquals("scope-synthetic-1", decision.scope.scopeId)
    }

    @Test
    fun takeoverRefusesStepsBeforeAnyScopeOrCapabilityCheck() {
        val probe = RecordingProbe()
        val controller = syntheticController(probe = probe)
        controller.lease.apply(ownerCommand(LeaseCommandKind.TAKE_OVER, epoch = 1))

        val decision = controller.authorizeStep("org.kaggle.app", "open")

        assertEquals(
            JobStepDecision.RefusedByLease(
                leaseEpoch = 2,
                packageName = "org.kaggle.app",
                actionId = "open",
                reason = LeaseRefusal.OWNER_TAKEOVER_ACTIVE,
            ),
            decision,
        )
        assertTrue("a paused job must cause no discovery", probe.calls.isEmpty())
    }

    @Test
    fun expiredLeaseRefusesStepsEvenWhenTheScopeWouldAllowThem() {
        val controller = syntheticController()
        now = CREATED_AT + LeaseBounds.MAX_DURATION_MILLIS

        assertEquals(
            LeaseRefusal.LEASE_EXPIRED,
            (controller.authorizeStep("org.kaggle.app", "open") as JobStepDecision.RefusedByLease).reason,
        )
    }

    @Test
    fun resumeRequiresAFreshStateCheckBeforeAnyNewStep() {
        val controller = syntheticController()
        controller.lease.apply(ownerCommand(LeaseCommandKind.TAKE_OVER, epoch = 1))
        controller.lease.apply(ownerCommand(LeaseCommandKind.RESUME, epoch = 2))

        assertEquals(
            LeaseRefusal.FRESH_STATE_REQUIRED,
            (controller.authorizeStep("org.kaggle.app", "open") as JobStepDecision.RefusedByLease).reason,
        )

        controller.lease.confirmFreshState(confirmingEpoch = 3)

        assertTrue(controller.authorizeStep("org.kaggle.app", "open") is JobStepDecision.Permitted)
        assertEquals(3, controller.lease.epoch)
    }

    @Test
    fun completionFromAnEarlierEpochIsDiscardedWithoutConsumingBudget() {
        val controller = syntheticController()
        val authorized = controller.authorizeStep("org.kaggle.app", "open") as JobStepDecision.Permitted
        controller.lease.apply(ownerCommand(LeaseCommandKind.TAKE_OVER, epoch = 1))

        val late = controller.recordCompletion(
            authorizedEpoch = authorized.leaseEpoch,
            packageName = "org.kaggle.app",
            actionId = "open",
        )

        assertEquals(
            JobStepDecision.RefusedByLease(
                leaseEpoch = 2,
                packageName = "org.kaggle.app",
                actionId = "open",
                reason = LeaseRefusal.STALE_EPOCH,
            ),
            late,
        )
        assertEquals(0, controller.enforcer().completedActionCount())
    }

    @Test
    fun completionUnderTheCurrentEpochConsumesExactlyOneBudgetUnit() {
        val controller = syntheticController()
        val authorized = controller.authorizeStep("org.kaggle.app", "open") as JobStepDecision.Permitted

        val recorded = controller.recordCompletion(
            authorizedEpoch = authorized.leaseEpoch,
            packageName = "org.kaggle.app",
            actionId = "open",
        )

        assertTrue(recorded is JobStepDecision.Permitted)
        assertEquals(1, controller.enforcer().completedActionCount())
        assertEquals(19, controller.enforcer().remainingActions())

        val again = controller.authorizeStep("org.kaggle.app", "open") as JobStepDecision.Permitted
        assertTrue("authorizing is side-effect free", again.leaseEpoch == 1)
        assertEquals(1, controller.enforcer().completedActionCount())
    }

    @Test
    fun resumeDoesNotRestorePermissionsBeyondTheScopeContract() {
        val controller = syntheticController()
        controller.lease.apply(ownerCommand(LeaseCommandKind.TAKE_OVER, epoch = 1))
        controller.lease.apply(ownerCommand(LeaseCommandKind.RESUME, epoch = 2))
        controller.lease.confirmFreshState(confirmingEpoch = 3)

        // Still in scope after resume.
        assertTrue(controller.authorizeStep("org.kaggle.app", "open") is JobStepDecision.Permitted)

        // Never granted by the contract, so resume cannot have restored it.
        assertEquals(
            ScopeRefusal.TARGET_NOT_IN_SCOPE,
            (controller.authorizeStep("org.colab.app", "open") as JobStepDecision.RefusedByScope).scope.reason,
        )
        assertEquals(
            ScopeRefusal.CLOUD_TRANSFER_NOT_PERMITTED,
            (
                controller.authorizeStep("org.kaggle.app", "open", cloudTransfer = true)
                    as JobStepDecision.RefusedByScope
                ).scope.reason,
        )

        // The registry pause survives independently of the lease.
        controller.registry().setOwnerEnabled("org.kaggle.app", ownerEnabled = false)
        val refused = controller.authorizeStep("org.kaggle.app", "open") as JobStepDecision.RefusedByScope
        assertEquals(ScopeRefusal.CAPABILITY_UNAVAILABLE, refused.scope.reason)
        assertEquals(UnavailableReason.APP_DISABLED_BY_OWNER.name, refused.scope.detail)
    }

    @Test
    fun lateResultsAreDiscardedByEpochAndAcceptedOnlyWhenCurrent() {
        val controller = syntheticController()
        assertEquals(ResultAcceptance.Accepted(epoch = 1), controller.acceptResult(submittedEpoch = 1))

        controller.lease.apply(ownerCommand(LeaseCommandKind.STOP, epoch = 1))

        assertEquals(
            ResultAcceptance.Discarded(
                submittedEpoch = 1,
                currentEpoch = 2,
                reason = LeaseRefusal.STALE_EPOCH,
            ),
            controller.acceptResult(submittedEpoch = 1),
        )
        assertEquals(
            ResultAcceptance.Discarded(
                submittedEpoch = 2,
                currentEpoch = 2,
                reason = LeaseRefusal.LEASE_TERMINATED,
            ),
            controller.acceptResult(submittedEpoch = 2),
        )
        assertEquals(
            ResultAcceptance.Discarded(
                submittedEpoch = 5,
                currentEpoch = 2,
                reason = LeaseRefusal.UNISSUED_EPOCH,
            ),
            controller.acceptResult(submittedEpoch = 5),
        )
        assertFalse(controller.acceptResult(submittedEpoch = 5) is ResultAcceptance.Accepted)
    }

    private fun syntheticController(probe: ActionRouteProbe = syntheticProbe()): TestController {
        val registry = SelectedAppCapabilityRegistry(probe).apply {
            select(syntheticApp("org.kaggle.app"))
            select(syntheticApp("org.bank.app"))
            declareAction("org.kaggle.app", openCapability())
            declareAction(
                "org.bank.app",
                AppActionCapability(
                    actionId = "pay-invoice",
                    label = "Pay invoice",
                    sensitivity = ActionSensitivity.MANUAL_ONLY,
                    supportedRoutes = setOf(ActionRoute.MANUAL_HANDOFF),
                ),
            )
        }
        val enforcer = ActionScopeEnforcer(syntheticContract(), registry, { now })
        val lease = ActionLease(
            createdAtEpochMillis = CREATED_AT,
            nowEpochMillis = { now },
        )
        return TestController(
            controller = BoundedJobController(lease, enforcer, { now }),
            registry = registry,
            enforcer = enforcer,
        )
    }

    private class TestController(
        private val controller: BoundedJobController,
        private val registry: SelectedAppCapabilityRegistry,
        private val enforcer: ActionScopeEnforcer,
    ) {
        val lease: ActionLease get() = controller.lease

        fun authorizeStep(packageName: String, actionId: String, cloudTransfer: Boolean = false) =
            controller.authorizeStep(packageName, actionId, cloudTransfer)

        fun recordCompletion(authorizedEpoch: Int, packageName: String, actionId: String) =
            controller.recordCompletion(authorizedEpoch, packageName, actionId)

        fun acceptResult(submittedEpoch: Int) = controller.acceptResult(submittedEpoch)

        fun registry() = registry

        fun enforcer() = enforcer
    }

    private class ProbeCall(val packageName: String, val actionId: String, val route: ActionRoute)

    private class RecordingProbe : ActionRouteProbe {
        val calls = mutableListOf<ProbeCall>()

        override fun isAvailable(packageName: String, actionId: String, route: ActionRoute): Boolean {
            calls += ProbeCall(packageName, actionId, route)
            return route == ActionRoute.APP_INTENT
        }
    }

    private fun syntheticProbe(): ActionRouteProbe = ActionRouteProbe { _, _, route ->
        route == ActionRoute.APP_INTENT
    }

    private fun syntheticApp(packageName: String) = SelectedApp(
        packageName = packageName,
        displayLabel = "Synthetic $packageName",
        ownerSelectedAtEpochMillis = CREATED_AT,
    )

    private fun openCapability() = AppActionCapability(
        actionId = "open",
        label = "Open competition",
        sensitivity = ActionSensitivity.STANDARD,
        supportedRoutes = setOf(ActionRoute.APP_INTENT, ActionRoute.MANUAL_HANDOFF),
    )

    private fun syntheticContract() = ScopeContract(
        id = "scope-synthetic-1",
        goal = "Open one synthetic competition page.",
        targets = listOf(
            ScopeTarget("org.kaggle.app", setOf("open")),
            ScopeTarget("org.bank.app", setOf("pay-invoice")),
        ),
        cloudTransferAllowed = false,
        completionMode = CompletionMode.OWNER_CONFIRMS_AT_END,
        createdAtEpochMillis = CREATED_AT,
    )

    private fun ownerCommand(kind: LeaseCommandKind, epoch: Int) = LeaseCommand(
        kind = kind,
        origin = LeaseCommandOrigin.OWNER_LOCAL,
        epoch = epoch,
        issuedAtEpochMillis = now,
    )

    private companion object {
        const val CREATED_AT = 1_700_000_000_000L
    }
}
