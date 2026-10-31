package io.github.mohamtur1.fourcbon2.guard

import org.junit.Assert.assertEquals
import org.junit.Test

class CriticalActionGuardTest {

    private val preview = CriticalActionGuard.Preview("Submit report", "competition-x", "hash-1")

    @Test
    fun sensitiveContextIsManualOnly() {
        val guard = CriticalActionGuard()
        for (context in listOf(
            CriticalActionGuard.SensitiveContext.PAYMENT,
            CriticalActionGuard.SensitiveContext.PASSWORD_OR_OTP,
            CriticalActionGuard.SensitiveContext.BIOMETRIC,
            CriticalActionGuard.SensitiveContext.PERMISSION_GRANT,
            CriticalActionGuard.SensitiveContext.SECURITY_SETTINGS,
            CriticalActionGuard.SensitiveContext.SUBMISSION,
        )) {
            val decision = guard.evaluate(preview, context, accountVerified = true, approvalGranted = true)
            assertEquals(CriticalActionGuard.Decision.MANUAL_ONLY, decision.decision)
        }
    }

    @Test
    fun allowedOnlyWithVerifiedAccountAndApproval() {
        val guard = CriticalActionGuard()
        assertEquals(
            CriticalActionGuard.Decision.ALLOWED,
            guard.evaluate(preview, CriticalActionGuard.SensitiveContext.NONE, true, true).decision,
        )
        assertEquals(
            CriticalActionGuard.Decision.REJECTED,
            guard.evaluate(preview, CriticalActionGuard.SensitiveContext.NONE, false, true).decision,
        )
        assertEquals(
            CriticalActionGuard.Decision.REJECTED,
            guard.evaluate(preview, CriticalActionGuard.SensitiveContext.NONE, true, false).decision,
        )
    }

    @Test
    fun uncertainOutcomeBlocksRetry() {
        val guard = CriticalActionGuard()
        val decision = guard.evaluate(
            preview,
            CriticalActionGuard.SensitiveContext.NONE,
            accountVerified = true,
            approvalGranted = true,
            previousOutcomeUncertain = true,
        )
        assertEquals(CriticalActionGuard.Decision.REJECTED, decision.decision)
        assertEquals("Uncertain previous outcome must be reconciled before any retry.", decision.reason)
    }
}
