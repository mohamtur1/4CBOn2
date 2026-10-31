package io.github.mohamtur1.fourcbon2.submission

import io.github.mohamtur1.fourcbon2.submission.SubmissionGate.Acknowledgment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmissionGateTest {

    private fun intent(gate: SubmissionGate, deadline: Long = Long.MAX_VALUE): String {
        return gate.saveIntent("competition-x", "hash-1", deadline).id
    }

    @Test
    fun fullAuthorizedFlow() {
        var now = 1000L
        val gate = SubmissionGate(clock = { now })
        val id = intent(gate)
        gate.requestAuthorization(id)
        gate.authorize(id)
        assertEquals(SubmissionGate.State.AUTHORIZED, gate.state(id))

        val outcome = gate.submit(id, Acknowledgment.ACKNOWLEDGED)
        assertTrue(outcome.accepted)
        assertEquals(SubmissionGate.State.SUBMITTED, gate.state(id))
    }

    @Test
    fun uncertainAcknowledgmentIsNeverSilentlyRetried() {
        var now = 1000L
        val gate = SubmissionGate(clock = { now })
        val id = intent(gate)
        gate.requestAuthorization(id)
        gate.authorize(id)
        val outcome = gate.submit(id, Acknowledgment.UNCERTAIN)
        assertFalse(outcome.accepted)
        assertEquals(SubmissionGate.State.UNCERTAIN_EXTERNAL_OUTCOME, gate.state(id))

        // Reconciliation is explicit and required before any retry.
        gate.reconcileUncertain(id)
        assertEquals(SubmissionGate.State.SUBMITTED, gate.state(id))
    }

    @Test
    fun rejectedAcknowledgmentKeepsIntentAuthorizedForReview() {
        var now = 1000L
        val gate = SubmissionGate(clock = { now })
        val id = intent(gate)
        gate.requestAuthorization(id)
        gate.authorize(id)
        val outcome = gate.submit(id, Acknowledgment.REJECTED)
        assertFalse(outcome.accepted)
        assertEquals(SubmissionGate.State.AUTHORIZED, gate.state(id))
    }

    @Test
    fun submittingWithoutAuthorizationIsRejected() {
        var now = 1000L
        val gate = SubmissionGate(clock = { now })
        val id = intent(gate)
        val outcome = gate.submit(id, Acknowledgment.ACKNOWLEDGED)
        assertFalse(outcome.accepted)
        assertEquals(SubmissionGate.State.INTENT_SAVED, gate.state(id))
    }

    @Test
    fun expiredIntentCannotSubmit() {
        var now = 1000L
        val gate = SubmissionGate(clock = { now })
        val id = intent(gate, deadline = 2000)
        gate.requestAuthorization(id)
        gate.authorize(id)
        now = 2001
        val outcome = gate.submit(id, Acknowledgment.ACKNOWLEDGED)
        assertFalse(outcome.accepted)
    }

    @Test
    fun denyAndCancelPaths() {
        var now = 1000L
        val gate = SubmissionGate(clock = { now })
        val id = intent(gate)
        gate.requestAuthorization(id)
        gate.deny(id)
        assertEquals(SubmissionGate.State.CANCELLED, gate.state(id))

        val id2 = intent(gate)
        gate.requestAuthorization(id2)
        gate.authorize(id2)
        gate.cancel(id2)
        assertEquals(SubmissionGate.State.CANCELLED, gate.state(id2))
    }

    @Test
    fun submittedIntentCannotBeCancelled() {
        var now = 1000L
        val gate = SubmissionGate(clock = { now })
        val id = intent(gate)
        gate.requestAuthorization(id)
        gate.authorize(id)
        gate.submit(id, Acknowledgment.ACKNOWLEDGED)
        assertFalse(gate.cancel(id)?.state == SubmissionGate.State.CANCELLED)
    }
}
