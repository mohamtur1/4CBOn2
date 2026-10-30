package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveActionApprovalGateTest {
    private var now = 1_700_000_000_000L

    private fun gate() = SensitiveActionApprovalGate { now }

    private fun confirmation(
        scopeId: String = "scope-1",
        actionKind: ApprovalActionKind = ApprovalActionKind.SEND_TO_RECIPIENT,
        targetIdentity: String = "synthetic-recipient",
        confirmedAt: Long = 1_700_000_000_000L,
        singleTap: Boolean = true,
    ) = ApprovalConfirmation(
        scopeId = scopeId,
        actionKind = actionKind,
        targetIdentity = targetIdentity,
        confirmedAtEpochMillis = confirmedAt,
        explicitSingleTap = singleTap,
    )

    private fun permitted(
        scopeId: String = "scope-1",
        actionId: String = "send",
        cloudTransfer: Boolean = false,
    ): ScopeDecision.Permitted = ScopeDecision.Permitted(
        scopeId = scopeId,
        packageName = "org.example.app",
        actionId = actionId,
        route = ActionRoute.APP_INTENT,
        sensitivity = ActionSensitivity.MANUAL_ONLY,
        requiresOwnerConfirmation = true,
        cloudTransfer = cloudTransfer,
    )

    private fun refusedScope(scopeId: String = "scope-1"): ScopeDecision.Refused = ScopeDecision.Refused(
        scopeId = scopeId,
        packageName = "org.example.app",
        actionId = "send",
        reason = ScopeRefusal.CLOUD_TRANSFER_NOT_PERMITTED,
    )

    private fun request(
        approvalId: String = "approval-1",
        scopeId: String = "scope-1",
        actionKind: ApprovalActionKind = ApprovalActionKind.SEND_TO_RECIPIENT,
        targetIdentity: String = "synthetic-recipient",
    ) = ApprovalRequest(
        approvalId = approvalId,
        scopeId = scopeId,
        actionKind = actionKind,
        targetIdentity = targetIdentity,
    )

    private fun issueDefault(
        subject: SensitiveActionApprovalGate = gate(),
        approvalId: String = "approval-1",
        maxUses: Int = 1,
    ): SensitiveActionApprovalGate {
        // Stamped at the current instant, so a helper call after the clock moves forward is
        // still a fresh confirmation rather than a stale one.
        val outcome = subject.issue(approvalId, confirmation(confirmedAt = now), durationMillis = 60_000L, maxUses = maxUses)
        assertTrue("expected issue to succeed but was $outcome", outcome is ApprovalOutcome.Issued)
        return subject
    }

    @Test
    fun matchingRequestInsideAPermittedScopeIsApproved() {
        val subject = issueDefault()

        val outcome = subject.authorise(request(), permitted())

        assertEquals(
            ApprovalOutcome.Approved("approval-1", ApprovalActionKind.SEND_TO_RECIPIENT, 0),
            outcome,
        )
    }

    @Test
    fun remainingUsesDecrementAndExhaustionRefuses() {
        val subject = issueDefault(maxUses = 2)

        assertEquals(
            ApprovalOutcome.Approved("approval-1", ApprovalActionKind.SEND_TO_RECIPIENT, 1),
            subject.authorise(request(), permitted()),
        )
        assertEquals(
            ApprovalOutcome.Approved("approval-1", ApprovalActionKind.SEND_TO_RECIPIENT, 0),
            subject.authorise(request(), permitted()),
        )
        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.APPROVAL_EXHAUSTED),
            subject.authorise(request(), permitted()),
        )
    }

    @Test
    fun singleUseApprovalCannotBeSpentTwice() {
        val subject = issueDefault(maxUses = 1)

        assertTrue(subject.authorise(request(), permitted()) is ApprovalOutcome.Approved)
        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.APPROVAL_EXHAUSTED),
            subject.authorise(request(), permitted()),
        )
        assertEquals(0, subject.approval("approval-1")?.remainingUses)
    }

    @Test
    fun failedRedemptionSpendsNoUse() {
        val subject = issueDefault(maxUses = 3)

        // Four ways to fail, none of which may burn the owner's budget.
        subject.authorise(request(targetIdentity = "someone-else"), permitted())
        subject.authorise(request(actionKind = ApprovalActionKind.SHARE_FILE), permitted())
        subject.authorise(request(scopeId = "scope-2"), permitted())
        subject.authorise(request(), refusedScope())

        assertEquals("a failed attempt must not consume a use", 3, subject.approval("approval-1")?.remainingUses)

        // The approval still works afterwards.
        assertTrue(subject.authorise(request(), permitted()) is ApprovalOutcome.Approved)
        assertEquals(2, subject.approval("approval-1")?.remainingUses)
    }

    @Test
    fun destinationIdentityMustMatchExactly() {
        val subject = issueDefault()

        // Prefix, suffix, substring, and case variants are all different destinations.
        listOf(
            "synthetic-recip",
            "synthetic-recipient-two",
            "nthetic-recipient",
            "Synthetic-Recipient",
            "SYNTHETIC-RECIPIENT",
            " synthetic-recipient",
            "synthetic-recipient ",
            "",
        ).forEach { candidate ->
            assertEquals(
                "expected an exact-match refusal for <$candidate>",
                ApprovalOutcome.Refused("approval-1", ApprovalRefusal.TARGET_MISMATCH),
                subject.authorise(request(targetIdentity = candidate), permitted()),
            )
        }
        assertEquals("no use may have been spent", 1, subject.approval("approval-1")?.remainingUses)
    }

    @Test
    fun approvalCannotBeUpgradedToADifferentKindOfAct() {
        val subject = issueDefault()
        val approval = subject.approval("approval-1")!!

        // A SEND approval can never authorise a CLOUD_TRANSFER or SHARE_FILE.
        listOf(ApprovalActionKind.CLOUD_TRANSFER, ApprovalActionKind.SHARE_FILE).forEach { other ->
            assertEquals(
                ApprovalOutcome.Refused("approval-1", ApprovalRefusal.ACTION_MISMATCH),
                subject.authorise(request(actionKind = other), permitted()),
            )
        }
        assertEquals(approval.maxUses, subject.approval("approval-1")?.remainingUses)
    }

    @Test
    fun approvalCannotBeSpentInsideADifferentJob() {
        val subject = issueDefault()

        // The request claims another job.
        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.SCOPE_MISMATCH),
            subject.authorise(request(scopeId = "scope-2"), permitted(scopeId = "scope-2")),
        )
        // The scope decision comes from another job.
        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.SCOPE_MISMATCH),
            subject.authorise(request(), permitted(scopeId = "scope-3")),
        )
    }

    @Test
    fun scopeRefusalCanNeverBeRescuedByAnApproval() {
        val subject = issueDefault()

        // Composition is conjunctive: the owner's approval does not override the job scope.
        val outcome = subject.authorise(request(), refusedScope())

        assertEquals(ApprovalOutcome.Refused("approval-1", ApprovalRefusal.SCOPE_NOT_PERMITTED), outcome)
        assertEquals("the approval is untouched", 1, subject.approval("approval-1")?.remainingUses)
    }

    @Test
    fun expiryIsCheckedInsideTheOnlyPermitPath() {
        val subject = issueDefault()
        assertEquals(1_700_000_060_000L, subject.approval("approval-1")?.expiresAtEpochMillis)

        // One millisecond before expiry it still works.
        now = 1_700_000_059_999L
        assertTrue(subject.authorise(request(), permitted()) is ApprovalOutcome.Approved)

        // A fresh single-use approval is issued at the current instant, so its window starts
        // here rather than at the first approval's start time.
        val second = issueDefault(approvalId = "approval-2", maxUses = 1)
        val secondExpiry = second.approval("approval-2")!!.expiresAtEpochMillis
        assertEquals(1_700_000_119_999L, secondExpiry)

        // One millisecond before its own expiry it still works...
        now = secondExpiry - 1
        assertEquals(
            ApprovalOutcome.Approved("approval-2", ApprovalActionKind.SEND_TO_RECIPIENT, 0),
            second.authorise(request(approvalId = "approval-2"), permitted()),
        )

        // ...and at its own expiry instant it is refused.
        val third = issueDefault(approvalId = "approval-3", maxUses = 1)
        now = third.approval("approval-3")!!.expiresAtEpochMillis
        assertEquals(
            ApprovalOutcome.Refused("approval-3", ApprovalRefusal.APPROVAL_EXPIRED),
            third.authorise(request(approvalId = "approval-3"), permitted()),
        )
        // An expired approval is also not reported as live.
        assertEquals(emptyList<SensitiveActionApproval>(), second.liveApprovals())
    }

    @Test
    fun revocationStopsFutureRedemption() {
        val subject = issueDefault(maxUses = 3)

        assertEquals(ApprovalOutcome.Revoked("approval-1"), subject.revoke("approval-1"))
        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.APPROVAL_REVOKED),
            subject.authorise(request(), permitted()),
        )
        // Revocation is terminal and idempotent in effect.
        assertEquals(ApprovalOutcome.Revoked("approval-1"), subject.revoke("approval-1"))
        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.APPROVAL_REVOKED),
            subject.authorise(request(), permitted()),
        )
        assertEquals(3, subject.approval("approval-1")?.remainingUses)
    }

    @Test
    fun unknownApprovalAndUnknownRevokeAreRefused() {
        val subject = gate()

        assertEquals(
            ApprovalOutcome.Refused("missing", ApprovalRefusal.UNKNOWN_APPROVAL),
            subject.authorise(request(approvalId = "missing"), permitted()),
        )
        assertEquals(
            ApprovalOutcome.Refused("missing", ApprovalRefusal.UNKNOWN_APPROVAL),
            subject.revoke("missing"),
        )
        assertNull(subject.approval("missing"))
    }

    @Test
    fun confirmationMustBePresentAndNameOneExactDestination() {
        val subject = gate()

        // Missing: blank or absent target.
        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.CONFIRMATION_MISSING),
            subject.issue("approval-1", confirmation(targetIdentity = "   "), 60_000L, 1),
        )

        // Ambiguous: a wildcard is not a destination.
        listOf("*", "recipient*", "?", "%", "any*one", "*@*").forEach { wildcard ->
            assertEquals(
                "expected a wildcard refusal for <$wildcard>",
                ApprovalOutcome.Refused("approval-1", ApprovalRefusal.CONFIRMATION_AMBIGUOUS),
                subject.issue("approval-1", confirmation(targetIdentity = wildcard), 60_000L, 1),
            )
        }

        assertNull("no approval may have been created", subject.approval("approval-1"))
        assertEquals(emptyList<SensitiveActionApproval>(), subject.liveApprovals())
    }

    @Test
    fun standingGrantIsRefusedInFavourOfOneDeliberateTap() {
        val subject = gate()

        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.CONFIRMATION_NOT_SINGLE_TAP),
            subject.issue("approval-1", confirmation(singleTap = false), 60_000L, 1),
        )
        assertNull(subject.approval("approval-1"))
    }

    @Test
    fun staleConfirmationIsRefused() {
        val subject = gate()

        // Confirmed just inside the freshness window.
        val fresh = subject.issue(
            "approval-fresh",
            confirmation(confirmedAt = now - ApprovalBounds.MAX_CONFIRMATION_AGE_MILLIS),
            60_000L,
            1,
        )
        assertTrue("a confirmation at the age limit is still current", fresh is ApprovalOutcome.Issued)

        // One millisecond older is stale.
        assertEquals(
            ApprovalOutcome.Refused("approval-stale", ApprovalRefusal.CONFIRMATION_STALE),
            subject.issue(
                "approval-stale",
                confirmation(confirmedAt = now - ApprovalBounds.MAX_CONFIRMATION_AGE_MILLIS - 1),
                60_000L,
                1,
            ),
        )
        assertNull(subject.approval("approval-stale"))
    }

    @Test
    fun confirmationChecksRunInTheDocumentedOrder() {
        val subject = gate()

        // Missing beats ambiguous: a blank target is never treated as a wildcard.
        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.CONFIRMATION_MISSING),
            subject.issue("approval-1", confirmation(targetIdentity = ""), 60_000L, 1),
        )
        // Ambiguous beats stale: a wildcard is reported as ambiguous even when also old.
        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.CONFIRMATION_AMBIGUOUS),
            subject.issue(
                "approval-1",
                confirmation(targetIdentity = "*", confirmedAt = now - 10 * 60_000L),
                60_000L,
                1,
            ),
        )
        // Not-a-single-tap beats stale: a standing grant is reported as such even when old.
        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.CONFIRMATION_NOT_SINGLE_TAP),
            subject.issue(
                "approval-1",
                confirmation(confirmedAt = now - 10 * 60_000L, singleTap = false),
                60_000L,
                1,
            ),
        )
    }

    @Test
    fun redemptionChecksRunInTheDocumentedOrder() {
        val subject = issueDefault(maxUses = 1)

        // Unknown beats everything else.
        assertEquals(
            ApprovalOutcome.Refused("nope", ApprovalRefusal.UNKNOWN_APPROVAL),
            subject.authorise(request(approvalId = "nope", scopeId = "other"), refusedScope()),
        )

        // Revoked beats expired.
        subject.revoke("approval-1")
        now += 10 * 60_000L
        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.APPROVAL_REVOKED),
            subject.authorise(request(), permitted()),
        )

        // Expired beats exhausted: a spent and expired approval reports expiry first.
        val second = gate()
        // The clock has moved, so the confirmation must be stamped at the current instant;
        // a default-timestamped one is now stale and would be refused for that reason instead.
        assertTrue(second.issue("approval-2", confirmation(confirmedAt = now), 60_000L, 1) is ApprovalOutcome.Issued)
        assertTrue(second.authorise(request(approvalId = "approval-2"), permitted()) is ApprovalOutcome.Approved)
        now += 10 * 60_000L
        assertEquals(
            ApprovalOutcome.Refused("approval-2", ApprovalRefusal.APPROVAL_EXPIRED),
            second.authorise(request(approvalId = "approval-2"), permitted()),
        )

        // Scope refusal beats mismatch: a refused scope is reported before any field comparison.
        val third = gate()
        assertTrue(third.issue("approval-3", confirmation(confirmedAt = now), 60_000L, 1) is ApprovalOutcome.Issued)
        assertEquals(
            ApprovalOutcome.Refused("approval-3", ApprovalRefusal.SCOPE_NOT_PERMITTED),
            third.authorise(
                request(approvalId = "approval-3", targetIdentity = "someone-else", scopeId = "scope-9"),
                refusedScope(),
            ),
        )
    }

    @Test
    fun duplicateIdsAndTheLiveCapAreEnforced() {
        val subject = gate()

        assertTrue(subject.issue("approval-1", confirmation(), 60_000L, 1) is ApprovalOutcome.Issued)
        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.DUPLICATE_APPROVAL_ID),
            subject.issue("approval-1", confirmation(), 60_000L, 1),
        )

        // Fill to the cap, then one more is refused rather than evicting an outstanding one.
        var index = 0
        while (subject.liveApprovals().size < ApprovalBounds.MAX_LIVE_APPROVALS) {
            index += 1
            val outcome = subject.issue("filler-$index", confirmation(), 60_000L, 1)
            assertTrue("filler $index should be issued", outcome is ApprovalOutcome.Issued)
        }
        assertEquals(ApprovalBounds.MAX_LIVE_APPROVALS, subject.liveApprovals().size)
        assertEquals(
            ApprovalOutcome.Refused("overflow", ApprovalRefusal.TOO_MANY_LIVE_APPROVALS),
            subject.issue("overflow", confirmation(), 60_000L, 1),
        )
        assertNull("nothing was silently evicted", subject.approval("overflow"))

        // An expired approval frees a slot, so the cap tracks live approvals rather than history.
        now += 10 * 60_000L
        assertEquals(emptyList<SensitiveActionApproval>(), subject.liveApprovals())
        assertTrue(subject.issue("overflow", confirmation(confirmedAt = now), 60_000L, 1) is ApprovalOutcome.Issued)
    }

    @Test
    fun approvalsLiveOnlyInMemoryAndDieWithTheGate() {
        val first = issueDefault(maxUses = 5)
        assertNotNull(first.approval("approval-1"))

        // A restarted process builds a new gate. Nothing is recovered, so a stale permission
        // cannot be redeemed after a restart. This is the safe direction: the owner is asked
        // again rather than an old approval being honoured.
        val second = gate()
        assertEquals(emptyList<SensitiveActionApproval>(), second.liveApprovals())
        assertNull(second.approval("approval-1"))
        assertEquals(
            ApprovalOutcome.Refused("approval-1", ApprovalRefusal.UNKNOWN_APPROVAL),
            second.authorise(request(), permitted()),
        )
    }

    @Test
    fun gateStaysInertWithRespectToRegistryScopeAndLease() {
        val subject = issueDefault(maxUses = 2)
        val registry = SelectedAppCapabilityRegistry(ActionRouteProbe.NONE)
        val lease = ActionLease(createdAtEpochMillis = now, nowEpochMillis = { now })
        lease.apply(
            LeaseCommand(LeaseCommandKind.STOP, LeaseCommandOrigin.OWNER_LOCAL, epoch = 1, issuedAtEpochMillis = now),
        )

        subject.authorise(request(), permitted())
        subject.liveApprovals()
        subject.revoke("approval-1")

        assertFalse(registry.isSelected("org.example.app"))
        assertEquals(
            CapabilityDecision.Unavailable("org.example.app", "send", UnavailableReason.APP_NOT_SELECTED),
            registry.resolve("org.example.app", "send"),
        )
        assertEquals(LeaseState.STOPPED, lease.state)
        assertEquals(LeaseRefusal.LEASE_TERMINATED, lease.stepRefusal(now))
    }

    @Test
    fun gateOffersNoWayToPerformTheActItAuthorises() {
        val forbidden = listOf(
            "run", "execute", "exec", "start", "launch", "dispatch", "perform", "invoke",
            "apply", "send", "share", "transfer", "upload", "upload", "post", "deliver", "fire",
        )
        val methodNames = SensitiveActionApprovalGate::class.java.declaredMethods.map { it.name.lowercase() }
        assertTrue("expected the gate to expose methods", methodNames.isNotEmpty())
        forbidden.forEach { word ->
            assertFalse("the gate must not expose a method named $word", methodNames.contains(word))
        }

        // No signature may accept or return a transport or execution handle.
        SensitiveActionApprovalGate::class.java.declaredMethods.forEach { method ->
            (method.parameterTypes.toList() + method.returnType).forEach { type ->
                val name = type.name
                listOf("Socket", "URL", "OutputStream", "File", "Registry", "Lease", "Executor", "Intent")
                    .forEach { boundary ->
                        assertFalse("unexpected boundary type $name in ${method.name}", name.contains(boundary))
                    }
            }
        }
    }

    @Test
    fun refusalsNeverRepeatTheApprovedDestination() {
        val subject = issueDefault(maxUses = 1)
        val sentinel = "sentinel-destination-value"

        val refusals = listOf(
            subject.authorise(request(targetIdentity = sentinel), permitted()),
            subject.authorise(request(), refusedScope()),
        )

        refusals.forEach { outcome ->
            assertTrue(outcome is ApprovalOutcome.Refused)
            val refused = outcome as ApprovalOutcome.Refused
            assertFalse("a refusal must not echo the request target", refused.detail.contains(sentinel))
            assertFalse("a refusal must not echo the approved target", refused.detail.contains("synthetic-recipient"))
            assertTrue("a refusal must carry a fixed phrase", refused.detail.isNotBlank())
        }
    }
}
