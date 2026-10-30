package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveActionApprovalTest {
    private var now = 1_700_000_000_000L

    private fun approval(
        approvalId: String = "approval-1",
        scopeId: String = "scope-1",
        actionKind: ApprovalActionKind = ApprovalActionKind.SEND_TO_RECIPIENT,
        targetIdentity: String = "synthetic-recipient",
        issuedAt: Long = 1_700_000_000_000L,
        durationMillis: Long = 60_000L,
        maxUses: Int = 1,
    ) = SensitiveActionApproval(
        approvalId = approvalId,
        scopeId = scopeId,
        actionKind = actionKind,
        targetIdentity = targetIdentity,
        issuedAtEpochMillis = issuedAt,
        durationMillis = durationMillis,
        maxUses = maxUses,
    )

    @Test
    fun boundedApprovalIsAcceptedAndExposesItsWindow() {
        val subject = approval(durationMillis = 60_000L, maxUses = 3)

        assertEquals(1_700_000_060_000L, subject.expiresAtEpochMillis)
        assertEquals(3, subject.remainingUses)
        assertEquals(3, subject.maxUses)
        assertFalse(subject.isRevoked)
        assertTrue(subject.isLiveAt(1_700_000_000_000L))
        // The window is half-open: the expiry instant itself is already too late.
        assertTrue(subject.isLiveAt(1_700_000_059_999L))
        assertFalse(subject.isLiveAt(1_700_000_060_000L))
    }

    @Test
    fun invalidIdentifiersAndTargetsAreRejected() {
        listOf("", "has space", "bad/slash", "a".repeat(65)).forEach { candidate ->
            assertThrows(IllegalArgumentException::class.java) { approval(approvalId = candidate) }
            assertThrows(IllegalArgumentException::class.java) { approval(scopeId = candidate) }
        }
        assertThrows(IllegalArgumentException::class.java) { approval(targetIdentity = "") }
        assertThrows(IllegalArgumentException::class.java) { approval(targetIdentity = "   ") }
        assertThrows(IllegalArgumentException::class.java) {
            approval(targetIdentity = "a".repeat(ApprovalBounds.MAX_TARGET_LENGTH + 1))
        }
    }

    @Test
    fun durationAndUseCapsCannotBeExceeded() {
        assertThrows(IllegalArgumentException::class.java) { approval(durationMillis = 0L) }
        assertThrows(IllegalArgumentException::class.java) { approval(durationMillis = -1L) }
        assertThrows(IllegalArgumentException::class.java) {
            approval(durationMillis = ApprovalBounds.MAX_DURATION_MILLIS + 1)
        }
        assertThrows(IllegalArgumentException::class.java) { approval(maxUses = 0) }
        assertThrows(IllegalArgumentException::class.java) { approval(maxUses = ApprovalBounds.MAX_USES + 1) }

        // Both caps are inclusive at the boundary, so they are limits rather than estimates.
        assertEquals(
            ApprovalBounds.MAX_DURATION_MILLIS,
            approval(durationMillis = ApprovalBounds.MAX_DURATION_MILLIS).durationMillis,
        )
        assertEquals(ApprovalBounds.MAX_USES, approval(maxUses = ApprovalBounds.MAX_USES).maxUses)
    }

    @Test
    fun approvalCannotOutliveTheJobItWasIssuedInside() {
        // An approval longer than a scope contract could live would be a permission
        // surviving the job that justified it.
        assertTrue(ApprovalBounds.MAX_DURATION_MILLIS <= ScopeBounds.MAX_DURATION_MILLIS)
    }

    @Test
    fun spendingConsumesExactlyOneUseAndExhaustionStopsAtZero() {
        val subject = approval(maxUses = 2)

        subject.spendOneUse()
        assertEquals(1, subject.remainingUses)
        assertTrue(subject.isLiveAt(now))

        subject.spendOneUse()
        assertEquals(0, subject.remainingUses)
        assertFalse("an exhausted approval is not live", subject.isLiveAt(now))
        assertThrows(IllegalStateException::class.java) { subject.spendOneUse() }
    }

    @Test
    fun revocationIsTerminal() {
        val subject = approval(maxUses = 5)

        subject.revoke()

        assertTrue(subject.isRevoked)
        assertFalse(subject.isLiveAt(now))
        assertThrows(IllegalStateException::class.java) { subject.spendOneUse() }
    }

    @Test
    fun approvalCarriesNoCredentialAccountOrExecutableField() {
        val fields = SensitiveActionApproval::class.java.declaredFields
            .map { it.name.lowercase() }
        listOf(
            "token", "credential", "password", "secret", "session", "cookie", "account",
            "url", "uri", "endpoint", "command", "package", "intent",
        ).forEach { forbidden ->
            assertFalse("no field should be named $forbidden", fields.any { it.contains(forbidden) })
        }
        assertTrue(
            "expected the documented bounded field set",
            fields.containsAll(
                listOf(
                    "approvalid", "scopeid", "actionkind", "targetidentity",
                    "issuedatepochmillis", "durationmillis", "maxuses",
                ),
            ),
        )
    }

    @Test
    fun actionKindEnumIsClosedAndHasNoWildcard() {
        assertEquals(
            listOf("SEND_TO_RECIPIENT", "CLOUD_TRANSFER", "SHARE_FILE"),
            ApprovalActionKind.entries.map { it.name },
        )
        val names = ApprovalActionKind.entries.map { it.name }
        listOf("ANY", "ALL", "OTHER", "WILDCARD", "SEND_ANY", "TRANSFER_ANY").forEach { forbidden ->
            assertFalse("ApprovalActionKind must not contain $forbidden", names.contains(forbidden))
        }
    }
}
