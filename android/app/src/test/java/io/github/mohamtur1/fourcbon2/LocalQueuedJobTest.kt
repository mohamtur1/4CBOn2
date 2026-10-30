package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalQueuedJobTest {
    private fun job(
        id: String = "job-1",
        summary: String = "Review the synthetic report",
        state: QueuedJobState = QueuedJobState.WAITING,
        deferral: DeferralReason? = null,
        queuedAt: Long = 1_700_000_000_000L,
        lastAction: Long = 1_700_000_000_000L,
    ) = LocalQueuedJob(
        id = id,
        summary = summary,
        state = state,
        deferral = deferral,
        queuedAtEpochMillis = queuedAt,
        lastOwnerActionAtEpochMillis = lastAction,
    )

    @Test
    fun boundedWaitingJobIsAccepted() {
        val subject = job(deferral = DeferralReason.AWAITING_OWNER_DECISION)

        assertEquals("job-1", subject.id)
        assertEquals(QueuedJobState.WAITING, subject.state)
        assertEquals(DeferralReason.AWAITING_OWNER_DECISION, subject.deferral)
    }

    @Test
    fun invalidIdentifiersAreRejected() {
        listOf("", "has space", "way-too-long-".repeat(6), "bad/slash").forEach { candidate ->
            assertThrows(IllegalArgumentException::class.java) { job(id = candidate) }
        }
    }

    @Test
    fun summaryIsBoundedAndFreeOfControlSeparators() {
        assertThrows(IllegalArgumentException::class.java) { job(summary = "") }
        assertThrows(IllegalArgumentException::class.java) { job(summary = "   ") }
        assertThrows(IllegalArgumentException::class.java) {
            job(summary = "a".repeat(LocalQueuedJob.MAX_SUMMARY_LENGTH + 1))
        }
        listOf("line\nbreak", "carriage\rreturn", "tab\there").forEach { candidate ->
            assertThrows(IllegalArgumentException::class.java) { job(summary = candidate) }
        }

        // Exactly at the cap is accepted, so the bound is a boundary rather than an estimate.
        assertEquals(
            LocalQueuedJob.MAX_SUMMARY_LENGTH,
            job(summary = "a".repeat(LocalQueuedJob.MAX_SUMMARY_LENGTH)).summary.length,
        )
    }

    @Test
    fun timestampsMustBePositiveAndOrdered() {
        assertThrows(IllegalArgumentException::class.java) { job(queuedAt = 0L, lastAction = 0L) }
        assertThrows(IllegalArgumentException::class.java) { job(queuedAt = -1L, lastAction = -1L) }
        // The last owner action can never predate the moment the job was queued.
        assertThrows(IllegalArgumentException::class.java) {
            job(queuedAt = 1_700_000_000_000L, lastAction = 1_699_999_999_999L)
        }
    }

    @Test
    fun terminalJobsMustNotCarryADeferralReason() {
        assertThrows(IllegalArgumentException::class.java) {
            job(state = QueuedJobState.CANCELLED, deferral = DeferralReason.NETWORK_UNAVAILABLE)
        }
        assertNull(job(state = QueuedJobState.CANCELLED).deferral)
    }

    @Test
    fun stateAndDeferralEnumsContainNoExecutionOutcome() {
        // The queue records wishes, not work. A value that could read as "this ran" would
        // let a restarted process imply something it never did.
        val forbiddenStates = listOf(
            "RUNNING", "EXECUTING", "ATTEMPTED", "SUCCEEDED", "FAILED", "COMPLETED",
            "DISPATCHED", "STARTED", "SENT", "DELIVERED", "RETRIED",
        )
        val stateNames = QueuedJobState.entries.map { it.name }
        assertEquals(listOf("WAITING", "CANCELLED"), stateNames)
        forbiddenStates.forEach { forbidden ->
            assertFalse("QueuedJobState must not contain $forbidden", stateNames.contains(forbidden))
        }

        // Every deferral reason is a local observation, never a remote result.
        val reasonNames = DeferralReason.entries.map { it.name }
        assertEquals(
            listOf("NETWORK_UNAVAILABLE", "AWAITING_OWNER_DECISION", "AWAITING_PERMISSION"),
            reasonNames,
        )
        forbiddenStates.forEach { forbidden ->
            assertFalse("DeferralReason must not contain $forbidden", reasonNames.contains(forbidden))
        }
    }

    @Test
    fun entityCarriesNoExecutableOrCredentialField() {
        // There is nothing here a machine could turn back into an action: no URIs, no
        // package names, no commands, no tokens.
        val fields = LocalQueuedJob::class.java.declaredFields
            .map { it.name.lowercase() }
        listOf("url", "uri", "intent", "command", "package", "path", "token", "credential", "endpoint")
            .forEach { forbidden ->
                assertFalse("no field should be named $forbidden", fields.any { it.contains(forbidden) })
            }
        assertTrue(
            "expected the documented bounded field set",
            fields.containsAll(
                listOf("id", "summary", "state", "deferral", "queuedatepochmillis", "lastowneractionatepochmillis"),
            ),
        )
    }
}
