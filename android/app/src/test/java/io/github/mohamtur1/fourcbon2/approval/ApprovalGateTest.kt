package io.github.mohamtur1.fourcbon2.approval

import io.github.mohamtur1.fourcbon2.local.ApprovalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalGateTest {

    @Test
    fun grantThenConsume() {
        var now = 1000L
        val gate = ApprovalGate(clock = { now })
        val request = gate.request("Submit report", "competition-x", "abc123", expiryEpochMs = 2000, maxActions = 1)
        assertEquals(ApprovalState.PENDING, gate.state(request.id))

        gate.grant(request.id)
        assertEquals(ApprovalState.GRANTED, gate.state(request.id))
        assertTrue(gate.consume(request.id, "competition-x", "abc123"))
        // Cap exhausted: the grant is consumed and cannot be reused.
        assertFalse(gate.consume(request.id, "competition-x", "abc123"))
    }

    @Test
    fun wrongDestinationOrArtifactIsRejected() {
        var now = 1000L
        val gate = ApprovalGate(clock = { now })
        val request = gate.request("Publish", "dest-a", "hash-1", expiryEpochMs = 2000, maxActions = 2)
        gate.grant(request.id)
        assertFalse(gate.consume(request.id, "dest-b", "hash-1"))
        assertFalse(gate.consume(request.id, "dest-a", "hash-2"))
        assertTrue(gate.consume(request.id, "dest-a", "hash-1"))
    }

    @Test
    fun expiredGrantIsRejected() {
        var now = 1000L
        val gate = ApprovalGate(clock = { now })
        val request = gate.request("Delete", "dest-a", "hash-1", expiryEpochMs = 1500, maxActions = 1)
        gate.grant(request.id)
        now = 1501
        assertFalse(gate.consume(request.id, "dest-a", "hash-1"))
        assertEquals(ApprovalState.EXPIRED, gate.state(request.id))
    }

    @Test
    fun denyAndCancelPaths() {
        var now = 1000L
        val gate = ApprovalGate(clock = { now })
        val request = gate.request("Do thing", "dest-a", "hash-1", expiryEpochMs = 2000, maxActions = 1)
        gate.deny(request.id)
        assertEquals(ApprovalState.DENIED, gate.state(request.id))
        assertFalse(gate.consume(request.id, "dest-a", "hash-1"))

        val second = gate.request("Do thing 2", "dest-a", "hash-1", expiryEpochMs = 2000, maxActions = 1)
        gate.cancel(second.id)
        assertEquals(ApprovalState.CANCELLED, gate.state(second.id))
        assertFalse(gate.consume(second.id, "dest-a", "hash-1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun pastExpiryRequestIsRejected() {
        var now = 5000L
        val gate = ApprovalGate(clock = { now })
        gate.request("X", "dest", "hash", expiryEpochMs = 1000, maxActions = 1)
    }
}
