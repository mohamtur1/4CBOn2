package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionScopeContractTest {
    @Test
    fun limitsRejectBudgetsWiderThanTheOwnerStartedProposal() {
        assertEquals(20, ScopeBounds.MAX_ACTIONS)
        assertEquals(300_000L, ScopeBounds.MAX_DURATION_MILLIS)
        assertEquals(20, ScopeLimits().maxActions)
        assertEquals(300_000L, ScopeLimits().durationMillis)

        listOf(0, -1, 21).forEach { maxActions ->
            assertThrows(IllegalArgumentException::class.java) {
                ScopeLimits(maxActions = maxActions)
            }
        }
        listOf(0L, -1L, 300_001L).forEach { durationMillis ->
            assertThrows(IllegalArgumentException::class.java) {
                ScopeLimits(durationMillis = durationMillis)
            }
        }

        assertEquals(1, ScopeLimits(maxActions = 1, durationMillis = 1L).maxActions)
    }

    @Test
    fun targetsRejectMalformedPackagesActionsAndDuplicatePackages() {
        assertThrows(IllegalArgumentException::class.java) {
            ScopeTarget("single", setOf("open"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScopeTarget("org.kaggle.app", emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScopeTarget("org.kaggle.app", setOf("Open App"))
        }

        val target = ScopeTarget("org.kaggle.app", setOf("open", "browse"))
        assertTrue(target.allows("open"))
        assertFalse(target.allows("submit"))

        assertThrows(IllegalArgumentException::class.java) {
            syntheticContract(
                targets = listOf(
                    ScopeTarget("org.kaggle.app", setOf("open")),
                    ScopeTarget("org.kaggle.app", setOf("browse")),
                ),
            )
        }
    }

    @Test
    fun cloudTransferDefaultsOffAndExpiryDerivesFromCreationTime() {
        val contract = syntheticContract()

        // Cloud transfer is opt-in only: an unconfigured contract must never carry data off device.
        assertFalse(contract.cloudTransferAllowed)

        assertEquals(1_700_000_000_000L, contract.createdAtEpochMillis)
        assertEquals(1_700_000_300_000L, contract.expiresAtEpochMillis)
        assertEquals(1L, contract.remainingMillisAt(1_700_000_299_999L))
        assertEquals(0L, contract.remainingMillisAt(1_700_000_400_000L))
        assertFalse(contract.isExpiredAt(1_700_000_299_999L))
        assertTrue(contract.isExpiredAt(1_700_000_300_000L))

        assertTrue(
            syntheticContract(cloudTransferAllowed = true).cloudTransferAllowed,
        )
    }

    @Test
    fun goalRecipientsAndFileLabelsAreBoundedAndControlFree() {
        assertThrows(IllegalArgumentException::class.java) {
            syntheticContract(goal = "   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticContract(goal = "a".repeat(1_001))
        }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticContract(goal = "line\nbreak")
        }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticContract(allowedRecipients = setOf(""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticContract(allowedFileLabels = setOf("tab\tname"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticContract(id = "bad id with spaces")
        }

        val contract = syntheticContract(
            allowedRecipients = setOf("owner-only"),
            allowedFileLabels = setOf("synthetic-report.pdf"),
        )
        assertEquals(setOf("owner-only"), contract.allowedRecipients)
        assertEquals(setOf("synthetic-report.pdf"), contract.allowedFileLabels)
        assertEquals(ScopeTarget("org.kaggle.app", setOf("open")), contract.targetFor("org.kaggle.app"))
        assertNull(contract.targetFor("org.unselected.app"))
    }

    @Test
    fun exclusionsRejectMalformedIdentifiersAndDefaultToEmpty() {
        assertThrows(IllegalArgumentException::class.java) {
            syntheticContract(excludedPackages = setOf("not-a-package"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticContract(excludedActionIds = setOf("Pay Invoice"))
        }

        val contract = syntheticContract(
            excludedPackages = setOf("org.bank.app"),
            excludedActionIds = setOf("submit"),
        )
        assertEquals(setOf("org.bank.app"), contract.excludedPackages)
        assertEquals(setOf("submit"), contract.excludedActionIds)
        assertTrue(syntheticContract().excludedPackages.isEmpty())
        assertTrue(syntheticContract().excludedActionIds.isEmpty())
    }

    private fun syntheticContract(
        id: String = "scope-synthetic-1",
        goal: String = "Open one synthetic competition page.",
        targets: List<ScopeTarget> = listOf(
            ScopeTarget("org.kaggle.app", setOf("open")),
            ScopeTarget("org.bank.app", setOf("pay-invoice")),
        ),
        allowedRecipients: Set<String> = emptySet(),
        allowedFileLabels: Set<String> = emptySet(),
        cloudTransferAllowed: Boolean = false,
        completionMode: CompletionMode = CompletionMode.OWNER_CONFIRMS_AT_END,
        limits: ScopeLimits = ScopeLimits(),
        createdAtEpochMillis: Long = 1_700_000_000_000L,
        excludedPackages: Set<String> = emptySet(),
        excludedActionIds: Set<String> = emptySet(),
    ) = ScopeContract(
        id = id,
        goal = goal,
        targets = targets,
        allowedRecipients = allowedRecipients,
        allowedFileLabels = allowedFileLabels,
        cloudTransferAllowed = cloudTransferAllowed,
        completionMode = completionMode,
        limits = limits,
        createdAtEpochMillis = createdAtEpochMillis,
        excludedPackages = excludedPackages,
        excludedActionIds = excludedActionIds,
    )
}
