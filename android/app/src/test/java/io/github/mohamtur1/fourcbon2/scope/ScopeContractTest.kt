package io.github.mohamtur1.fourcbon2.scope

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeContractTest {

    @Test
    fun allowsConfiguredDestinationsOnly() {
        val contract = ScopeContract(
            ScopeLimits(
                allowedDestinations = listOf("com.example.notes"),
                maxActions = 3,
                expiryEpochMs = Long.MAX_VALUE,
                allowCloudUpload = false,
            ),
        )
        assertTrue(contract.beginAction("com.example.notes", "open"))
        assertFalse(contract.beginAction("com.example.bank", "open"))
    }

    @Test
    fun expiredScopeDeniesEverything() {
        var now = 1000L
        val contract = ScopeContract(
            scope = ScopeLimits(
                allowedDestinations = listOf("com.example.notes"),
                maxActions = 5,
                expiryEpochMs = 2000L,
                allowCloudUpload = false,
            ),
            clock = { now },
        )
        assertTrue(contract.beginAction("com.example.notes", "open"))
        now = 2001
        assertFalse(contract.beginAction("com.example.notes", "open"))
    }

    @Test
    fun cloudTransferRequiresExplicitAllowance() {
        val contract = ScopeContract(
            ScopeLimits(
                allowedDestinations = listOf("com.example.notes"),
                maxActions = 5,
                expiryEpochMs = Long.MAX_VALUE,
                allowCloudUpload = false,
            ),
        )
        assertFalse(contract.beginAction("com.example.notes", "upload", isCloudTransfer = true))
    }

    @Test
    fun actionCountIsCapped() {
        val contract = ScopeContract(
            ScopeLimits(
                allowedDestinations = listOf("com.example.notes"),
                maxActions = 2,
                expiryEpochMs = Long.MAX_VALUE,
                allowCloudUpload = false,
            ),
        )
        assertTrue(contract.beginAction("com.example.notes", "open"))
        assertTrue(contract.beginAction("com.example.notes", "open"))
        assertFalse(contract.beginAction("com.example.notes", "open"))
    }
}
