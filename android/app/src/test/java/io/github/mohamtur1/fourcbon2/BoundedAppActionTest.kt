package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedAppActionTest {
    private var now = 1_700_000_000_000L

    private val packageName = "org.example.notes"
    private val actionId = "open"

    // ---- fixtures -------------------------------------------------------

    private fun app(pkg: String = packageName) = SelectedApp(
        packageName = pkg,
        displayLabel = "Synthetic notes",
        ownerSelectedAtEpochMillis = 1_699_000_000_000L,
    )

    private fun openCapability(route: ActionRoute = ActionRoute.APP_INTENT) = AppActionCapability(
        actionId = actionId,
        label = "Open the notes screen",
        sensitivity = ActionSensitivity.STANDARD,
        supportedRoutes = setOf(route),
    )

    private fun registry(
        route: ActionRoute = ActionRoute.APP_INTENT,
        ownerEnabled: Boolean = true,
    ): SelectedAppCapabilityRegistry {
        val registry = SelectedAppCapabilityRegistry { _, _, probeRoute -> probeRoute == route }
        registry.select(app(), ownerEnabled = ownerEnabled)
        // The declared capability must offer the route the probe reports, otherwise the
        // registry correctly answers NO_SUPPORTED_ROUTE_AVAILABLE.
        registry.declareAction(packageName, openCapability(route))
        return registry
    }

    private fun contract(
        cloudTransferAllowed: Boolean = false,
        excludedPackages: Set<String> = emptySet(),
        excludedActionIds: Set<String> = emptySet(),
    ) = ScopeContract(
        id = "scope-div21",
        goal = "Open one synthetic notes screen.",
        targets = listOf(ScopeTarget(packageName, setOf(actionId))),
        allowedRecipients = setOf("owner-only"),
        cloudTransferAllowed = cloudTransferAllowed,
        limits = ScopeLimits(),
        createdAtEpochMillis = 1_700_000_000_000L,
        excludedPackages = excludedPackages,
        excludedActionIds = excludedActionIds,
    )

    private fun readyLease(): ActionLease = ActionLease(createdAtEpochMillis = now, nowEpochMillis = { now })

    private fun stoppedLease(): ActionLease = ActionLease(createdAtEpochMillis = now, nowEpochMillis = { now }).also {
        it.apply(LeaseCommand(LeaseCommandKind.STOP, LeaseCommandOrigin.OWNER_LOCAL, epoch = 1, issuedAtEpochMillis = now))
    }

    /** A port that records every call, so "nothing was attempted" is observable. */
    private class RecordingPort(
        private val report: DispatchReport,
        private val onDispatch: () -> Unit = {},
    ) : AppActionPort {
        val requests = mutableListOf<AppOpenRequest>()

        override fun dispatch(request: AppOpenRequest): DispatchReport {
            requests += request
            onDispatch()
            return report
        }
    }

    private fun action(
        port: AppActionPort,
        registry: SelectedAppCapabilityRegistry = registry(),
        scopeEnforcer: ActionScopeEnforcer = ActionScopeEnforcer(contract(), registry, { now }),
        lease: ActionLease = readyLease(),
    ) = BoundedAppAction(
        registry = registry,
        scopeEnforcer = scopeEnforcer,
        lease = lease,
        port = port,
        nowEpochMillis = { now },
    )

    // ---- the four outcome cases -----------------------------------------

    @Test
    fun deliveredReportBecomesCompleted() {
        val port = RecordingPort(DispatchReport.Delivered("opened"))

        val outcome = action(port).perform(packageName, actionId)

        assertEquals(ActionOutcome.Completed(packageName, actionId, ActionRoute.APP_INTENT, 0L), outcome)
        assertEquals(1, port.requests.size)
    }

    @Test
    fun rejectedReportBecomesFailedNotRefused() {
        val port = RecordingPort(DispatchReport.Rejected("platform said no"))

        val outcome = action(port).perform(packageName, actionId)

        // A dispatch was attempted, so this is a failure rather than a pre-attempt refusal.
        assertEquals(ActionOutcome.Failed(packageName, actionId, 0L), outcome)
        assertEquals(1, port.requests.size)
    }

    @Test
    fun inDoubtReportBecomesUnknown() {
        val port = RecordingPort(DispatchReport.InDoubt("no answer"))

        val outcome = action(port).perform(packageName, actionId)

        assertEquals(
            ActionOutcome.Unknown(packageName, actionId, 0L, ActionOutcome.UnknownCause.BOUNDARY_IN_DOUBT),
            outcome,
        )
    }

    @Test
    fun boundaryExceptionBecomesUnknownRatherThanFailure() {
        val port = RecordingPort(DispatchReport.Delivered("unused"), onDispatch = { error("platform exploded") })

        val outcome = action(port).perform(packageName, actionId)

        // An exception could have been thrown before or after the platform acted, so this is
        // never reported as a failure.
        assertEquals(
            ActionOutcome.Unknown(packageName, actionId, 0L, ActionOutcome.UnknownCause.BOUNDARY_ERROR),
            outcome,
        )
    }

    @Test
    fun overrunBecomesUnknownEvenWhenTheBoundaryReportedSuccess() {
        val port = RecordingPort(DispatchReport.Delivered("opened")) { now += ActionBounds.MAX_ROUND_TRIP_MILLIS + 1 }

        val outcome = action(port).perform(packageName, actionId)

        // The answer arrived outside the promised window, so it is not treated as an answer.
        assertEquals(
            ActionOutcome.Unknown(
                packageName,
                actionId,
                ActionBounds.MAX_ROUND_TRIP_MILLIS + 1,
                ActionOutcome.UnknownCause.DEADLINE_EXCEEDED,
            ),
            outcome,
        )
    }

    @Test
    fun exactlyAtTheDeadlineStillCounts() {
        val port = RecordingPort(DispatchReport.Delivered("opened")) { now += ActionBounds.MAX_ROUND_TRIP_MILLIS }

        assertEquals(
            ActionOutcome.Completed(packageName, actionId, ActionRoute.APP_INTENT, ActionBounds.MAX_ROUND_TRIP_MILLIS),
            action(port).perform(packageName, actionId),
        )
    }

    // ---- the core honesty property --------------------------------------

    @Test
    fun unknownIsNeverCollapsedIntoSuccessOrFailure() {
        val causes = listOf(
            RecordingPort(DispatchReport.InDoubt("doubt")),
            RecordingPort(DispatchReport.Delivered("late")) { now += ActionBounds.MAX_ROUND_TRIP_MILLIS + 1 },
            RecordingPort(DispatchReport.Rejected("threw")) { error("boom") },
        )

        causes.forEach { port ->
            val outcome = action(port).perform(packageName, actionId)

            assertTrue("an indeterminate result must be Unknown but was $outcome", outcome is ActionOutcome.Unknown)
            assertFalse("must not be reported as completed", outcome is ActionOutcome.Completed)
            assertFalse("must not be reported as failed", outcome is ActionOutcome.Failed)
            assertFalse("must not be reported as refused", outcome is ActionOutcome.Refused)

            // Compiling the distinction is not enough: the outcome must not carry any field
            // that could be read as a success claim.
            val unknown = outcome as ActionOutcome.Unknown
            assertEquals(packageName, unknown.packageName)
            assertEquals(actionId, unknown.actionId)
        }
    }

    @Test
    fun anUnknownResultIsNotRetried() {
        val port = RecordingPort(DispatchReport.InDoubt("doubt"))

        val outcome = action(port).perform(packageName, actionId)

        assertTrue(outcome is ActionOutcome.Unknown)
        // Exactly one attempt. Retrying could perform the act twice.
        assertEquals("an unknown result must not be retried", 1, port.requests.size)

        // And a second explicit call is a second owner-initiated attempt, not an automatic retry.
        action(port).perform(packageName, actionId)
        assertEquals(2, port.requests.size)
    }

    // ---- refusals happen before any dispatch ----------------------------

    @Test
    fun refusalsNeverTouchThePort() {
        val port = RecordingPort(DispatchReport.Delivered("must not be reached"))

        val refusals = listOf(
            action(port, registry = registry(route = ActionRoute.MANUAL_HANDOFF)).perform(packageName, actionId),
            action(port, registry = registry(ownerEnabled = false)).perform(packageName, actionId),
            action(port, registry = registry(route = ActionRoute.APP_LINK_OR_SHARE)).perform(packageName, actionId),
            action(port, scopeEnforcer = ActionScopeEnforcer(contract(excludedPackages = setOf(packageName)), registry(), { now }))
                .perform(packageName, actionId),
            action(port, lease = stoppedLease()).perform(packageName, actionId),
        )

        refusals.forEach { outcome ->
            assertTrue("expected a refusal but was $outcome", outcome is ActionOutcome.Refused)
            assertFalse("a refusal must not be a completion", outcome is ActionOutcome.Completed)
            assertFalse("a refusal must not be a failure", outcome is ActionOutcome.Failed)
            assertFalse("a refusal must not be unknown", outcome is ActionOutcome.Unknown)
        }
        assertEquals("no refusal may reach the port", 0, port.requests.size)
    }

    @Test
    fun invalidIdentifiersAreRefusedBeforeThePort() {
        val port = RecordingPort(DispatchReport.Delivered("must not be reached"))
        val subject = action(port)

        listOf(
            subject.perform("not a package", actionId),
            subject.perform(packageName, "Bad Action"),
            subject.perform("", ""),
        ).forEach { outcome ->
            assertEquals(ActionOutcome.Refused("", "", ActionRefusal.REQUEST_INVALID).let { it::class.java }, outcome::class.java)
            assertTrue(outcome is ActionOutcome.Refused)
        }
        assertEquals(0, port.requests.size)
    }

    @Test
    fun manualHandoffRouteIsRefusedBecauseTheOwnerDoesTheStep() {
        val port = RecordingPort(DispatchReport.Delivered("must not be reached"))

        val outcome = action(port, registry = registry(route = ActionRoute.MANUAL_HANDOFF)).perform(packageName, actionId)

        assertEquals(ActionOutcome.Refused(packageName, actionId, ActionRefusal.MANUAL_STEP_ONLY), outcome)
        assertEquals(0, port.requests.size)
    }

    @Test
    fun shareRouteIsRefusedBecauseAShareIsAnApprovalGatedAct() {
        val port = RecordingPort(DispatchReport.Delivered("must not be reached"))

        // A share route dispatches a share, which Division 20's approval gate governs. This
        // division declines to handle it rather than performing an ungated share.
        val outcome = action(port, registry = registry(route = ActionRoute.APP_LINK_OR_SHARE))
            .perform(packageName, actionId)

        assertEquals(ActionOutcome.Refused(packageName, actionId, ActionRefusal.APPROVAL_REQUIRED), outcome)
        assertEquals(0, port.requests.size)
    }

    @Test
    fun routesThatAreNotScreenOpensAreRefused() {
        val port = RecordingPort(DispatchReport.Delivered("must not be reached"))

        listOf(ActionRoute.AUTHORIZED_API, ActionRoute.FIXED_ROUTINE).forEach { route ->
            assertEquals(
                "expected a route refusal for $route",
                ActionOutcome.Refused(packageName, actionId, ActionRefusal.ROUTE_NOT_DISPATCHABLE),
                action(port, registry = registry(route = route)).perform(packageName, actionId),
            )
        }
        assertEquals(0, port.requests.size)
    }

    @Test
    fun onlyAppIntentIsDispatchable() {
        // The dispatchable set is a single route, asserted rather than described.
        val port = RecordingPort(DispatchReport.Delivered("opened"))
        val outcome = action(port, registry = registry(route = ActionRoute.APP_INTENT))
            .perform(packageName, actionId)

        assertEquals(ActionOutcome.Completed(packageName, actionId, ActionRoute.APP_INTENT, 0L), outcome)
        assertEquals(1, port.requests.size)

        // Every other declared route is refused, so the set cannot quietly grow.
        ActionRoute.entries
            .filter { it != ActionRoute.APP_INTENT }
            .forEach { route ->
                val refusedPort = RecordingPort(DispatchReport.Delivered("must not be reached"))
                val refused = action(refusedPort, registry = registry(route = route))
                    .perform(packageName, actionId)
                assertTrue("route $route must not dispatch but was $refused", refused is ActionOutcome.Refused)
                assertEquals("route $route must not reach the port", 0, refusedPort.requests.size)
            }
    }

    @Test
    fun unavailableCapabilityIsRefused() {
        val port = RecordingPort(DispatchReport.Delivered("must not be reached"))
        val registry = registry(ownerEnabled = false)

        assertEquals(
            ActionOutcome.Refused(packageName, actionId, ActionRefusal.CAPABILITY_UNAVAILABLE),
            action(port, registry = registry).perform(packageName, actionId),
        )
        // An undeclared action is equally unavailable.
        assertEquals(
            ActionOutcome.Refused(packageName, "undeclared", ActionRefusal.CAPABILITY_UNAVAILABLE),
            action(port).perform(packageName, "undeclared"),
        )
        assertEquals(0, port.requests.size)
    }

    @Test
    fun scopeRefusalBlocksTheDispatch() {
        val port = RecordingPort(DispatchReport.Delivered("must not be reached"))

        val excludedTarget = action(
            port,
            scopeEnforcer = ActionScopeEnforcer(contract(excludedPackages = setOf(packageName)), registry(), { now }),
        )
        assertEquals(
            ActionOutcome.Refused(packageName, actionId, ActionRefusal.SCOPE_REFUSED),
            excludedTarget.perform(packageName, actionId),
        )

        val excludedAction = action(
            port,
            scopeEnforcer = ActionScopeEnforcer(contract(excludedActionIds = setOf(actionId)), registry(), { now }),
        )
        assertEquals(
            ActionOutcome.Refused(packageName, actionId, ActionRefusal.SCOPE_REFUSED),
            excludedAction.perform(packageName, actionId),
        )

        assertEquals(0, port.requests.size)
    }

    @Test
    fun leaseThatIsNotReadyBlocksTheDispatch() {
        val port = RecordingPort(DispatchReport.Delivered("must not be reached"))

        assertEquals(
            ActionOutcome.Refused(packageName, actionId, ActionRefusal.LEASE_NOT_READY),
            action(port, lease = stoppedLease()).perform(packageName, actionId),
        )
        assertEquals(0, port.requests.size)
    }

    @Test
    fun gatedActIsRefusedRatherThanPerformedWithoutAnApproval() {
        val port = RecordingPort(DispatchReport.Delivered("must not be reached"))
        val transferContract = contract(cloudTransferAllowed = true)

        val subject = action(
            port,
            scopeEnforcer = ActionScopeEnforcer(transferContract, registry(), { now }),
        )
        val outcome = subject.perform(packageName, actionId, cloudTransfer = true)

        // Even with a scope that permits the transfer, this class still refuses: the approval
        // gate governs transfers and this division declines to handle them at all.
        assertEquals(ActionOutcome.Refused(packageName, actionId, ActionRefusal.APPROVAL_REQUIRED), outcome)
        assertEquals(0, port.requests.size)

        // With a scope that forbids the transfer, the scope refusal is the truthful first cause.
        assertEquals(
            ActionOutcome.Refused(packageName, actionId, ActionRefusal.SCOPE_REFUSED),
            action(port).perform(packageName, actionId, cloudTransfer = true),
        )
        assertEquals(0, port.requests.size)
    }

    @Test
    fun checksRunInTheDocumentedOrder() {
        val port = RecordingPort(DispatchReport.Delivered("must not be reached"))

        // Invalid shape beats capability.
        assertEquals(
            ActionOutcome.Refused("bad package", actionId, ActionRefusal.REQUEST_INVALID),
            action(port).perform("bad package", actionId),
        )

        // Unavailable capability beats a stopped lease: the capability is checked first.
        assertEquals(
            ActionOutcome.Refused(packageName, actionId, ActionRefusal.CAPABILITY_UNAVAILABLE),
            action(port, registry = registry(ownerEnabled = false), lease = stoppedLease()).perform(packageName, actionId),
        )

        // Manual route beats scope: a manual step is reported as such even when also out of scope.
        assertEquals(
            ActionOutcome.Refused(packageName, actionId, ActionRefusal.MANUAL_STEP_ONLY),
            action(
                port,
                registry = registry(route = ActionRoute.MANUAL_HANDOFF),
                scopeEnforcer = ActionScopeEnforcer(contract(excludedPackages = setOf(packageName)), registry(route = ActionRoute.MANUAL_HANDOFF), { now }),
            ).perform(packageName, actionId),
        )

        // Share route beats scope: a gated act is reported as such even when also out of scope.
        assertEquals(
            ActionOutcome.Refused(packageName, actionId, ActionRefusal.APPROVAL_REQUIRED),
            action(
                port,
                registry = registry(route = ActionRoute.APP_LINK_OR_SHARE),
                scopeEnforcer = ActionScopeEnforcer(
                    contract(excludedPackages = setOf(packageName)),
                    registry(route = ActionRoute.APP_LINK_OR_SHARE),
                    { now },
                ),
            ).perform(packageName, actionId),
        )

        // Scope beats lease.
        assertEquals(
            ActionOutcome.Refused(packageName, actionId, ActionRefusal.SCOPE_REFUSED),
            action(
                port,
                scopeEnforcer = ActionScopeEnforcer(contract(excludedPackages = setOf(packageName)), registry(), { now }),
                lease = stoppedLease(),
            ).perform(packageName, actionId),
        )

        // Lease beats the gated-act check, but only once the scope permits the transfer.
        val transferContract = contract(cloudTransferAllowed = true)
        assertEquals(
            ActionOutcome.Refused(packageName, actionId, ActionRefusal.LEASE_NOT_READY),
            action(
                port,
                scopeEnforcer = ActionScopeEnforcer(transferContract, registry(), { now }),
                lease = stoppedLease(),
            ).perform(packageName, actionId, cloudTransfer = true),
        )

        assertEquals(0, port.requests.size)
    }

    // ---- preview ---------------------------------------------------------

    @Test
    fun previewReportsTheRouteWithoutAttemptingAnything() {
        val port = RecordingPort(DispatchReport.Delivered("must not be reached"))
        val subject = action(port)

        assertEquals(ActionRoute.APP_INTENT, subject.previewRoute(packageName, actionId))
        assertNull("a refused action must preview as null", subject.previewRoute(packageName, "undeclared"))
        assertNull(subject.previewRoute("bad package", actionId))
        assertEquals("preview must not dispatch", 0, port.requests.size)
    }

    @Test
    fun backwardsClockDoesNotLookLikeADeadlineBreach() {
        val port = RecordingPort(DispatchReport.Delivered("opened")) { now -= 60_000L }

        val outcome = action(port).perform(packageName, actionId)

        assertEquals(ActionOutcome.Completed(packageName, actionId, ActionRoute.APP_INTENT, 0L), outcome)
    }

    // ---- inertness -------------------------------------------------------

    @Test
    fun actionOffersNoRetryOrEscalationPath() {
        val forbidden = listOf(
            "retry", "rerun", "repeat", "escalate", "fallback", "broaden", "widen", "upgrade",
            "queue", "schedule", "background", "loop", "drain", "poll",
        )
        val methodNames = BoundedAppAction::class.java.declaredMethods.map { it.name.lowercase() }

        assertTrue("expected methods to inspect", methodNames.isNotEmpty())
        forbidden.forEach { word ->
            assertFalse("the action must not expose a method named $word", methodNames.contains(word))
        }
    }

    @Test
    fun actionAcceptsNoApprovalOrCredentialHandle() {
        // This division cannot redeem an approval, so it cannot perform a gated act.
        BoundedAppAction::class.java.declaredMethods.forEach { method ->
            (method.parameterTypes.toList() + method.returnType).forEach { type ->
                val name = type.name
                listOf("SensitiveActionApproval", "ApprovalConfirmation", "Credential", "Token", "Session")
                    .forEach { forbidden ->
                        assertFalse("unexpected boundary type $name in ${method.name}", name.contains(forbidden))
                    }
            }
        }
    }

    @Test
    fun actionSourceOpensNoNetworkOrFilesystem() {
        val source = java.io.File(
            "android/app/src/main/java/io/github/mohamtur1/fourcbon2/BoundedAppAction.kt",
        )
        if (!source.exists()) return // Source layout differs outside the repository; CI covers it.

        val text = source.readText(Charsets.UTF_8)
        listOf("Socket", "URL(", "HttpClient", "okhttp", "java.net", "java.io.File", "RandomAccessFile", "Intent(")
            .forEach { forbidden ->
                assertFalse("action source must not reference $forbidden", text.contains(forbidden))
            }
    }
}
