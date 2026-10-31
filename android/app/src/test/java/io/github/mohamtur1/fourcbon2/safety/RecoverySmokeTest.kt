package io.github.mohamtur1.fourcbon2.safety

import io.github.mohamtur1.fourcbon2.control.TakeoverLease
import io.github.mohamtur1.fourcbon2.local.JobQueue
import io.github.mohamtur1.fourcbon2.local.JobState
import io.github.mohamtur1.fourcbon2.local.JobRecord
import io.github.mohamtur1.fourcbon2.local.LocalDocumentStore
import io.github.mohamtur1.fourcbon2.local.ResourceCaps
import io.github.mohamtur1.fourcbon2.routines.RoutineEngine
import io.github.mohamtur1.fourcbon2.scope.ScopeContract
import io.github.mohamtur1.fourcbon2.scope.ScopeLimits
import io.github.mohamtur1.fourcbon2.submission.SubmissionGate
import io.github.mohamtur1.fourcbon2.submission.SubmissionGate.Acknowledgment
import io.github.mohamtur1.fourcbon2.testutil.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused recovery/safety smoke tests (Division 41).
 *
 * Offline/reconnect, stale cloud response, interrupted work, revoked
 * permission, ambiguous acknowledgment, and duplicate-action fixtures.
 */
class RecoverySmokeTest {

    private fun job(id: String, state: JobState = JobState.DRAFT) =
        JobRecord(
            id = id,
            ownerAccount = "owner",
            goal = "goal",
            workflowVersion = 1,
            inputs = emptyMap(),
            artifactHashes = emptyMap(),
            baseRevision = "rev-1",
            resourceCaps = ResourceCaps(maxActions = 1, maxUploadBytes = 1024, maxMinutes = 1),
            dependencies = emptyList(),
            deadlineEpochMs = Long.MAX_VALUE,
            authorizationScope = "local-only",
            state = state,
        )

    @Test
    fun offlineJobWaitsAndReconnectReconciles() {
        val dir = createTempDirectory("smoke-offline")
        val offline = JobQueue(LocalDocumentStore(dir))
        offline.load()
        offline.add(job("j1"))
        offline.transition("j1", JobState.WAITING_FOR_INTERNET)

        val reconnected = JobQueue(LocalDocumentStore(dir))
        reconnected.load()
        assertEquals(1, reconnected.list(JobState.WAITING_FOR_INTERNET).size)
        // Reconnect does not auto-dispatch waiting jobs.
        assertTrue(reconnected.dispatchable().none { it.id == "j1" })
    }

    @Test
    fun staleCloudResponseCannotActAfterTakeover() {
        val lease = TakeoverLease()
        lease.beginControl("owner")
        assertTrue(lease.acceptCommand(commandEpoch = 0, commandOwner = "owner"))
        lease.takeOver("owner")
        assertFalse(lease.acceptCommand(commandEpoch = 0, commandOwner = "owner"))
        assertFalse(lease.acceptCommand(commandEpoch = 1, commandOwner = "owner"))
    }

    @Test
    fun interruptedWorkIsNotReplayedAfterRestart() {
        val dir = createTempDirectory("smoke-interrupted")
        val first = JobQueue(LocalDocumentStore(dir))
        first.load()
        first.add(job("j1"))
        first.prepareForDispatch("j1")
        first.transition("j1", JobState.RUNNING)

        val second = JobQueue(LocalDocumentStore(dir))
        second.load()
        assertFalse(second.dispatchable().any { it.id == "j1" })
    }

    @Test
    fun revokedPermissionDeniesActions() {
        var now = 1000L
        val contract = ScopeContract(
            ScopeLimits(
                allowedDestinations = listOf("com.example.notes"),
                maxActions = 5,
                expiryEpochMs = 1500,
                allowCloudUpload = false,
            ),
            clock = { now },
        )
        assertTrue(contract.beginAction("com.example.notes", "open"))
        now = 1501
        assertFalse(contract.beginAction("com.example.notes", "open"))
    }

    @Test
    fun ambiguousAcknowledgmentBlocksRetry() {
        var now = 1000L
        val gate = SubmissionGate(clock = { now })
        val intent = gate.saveIntent("competition-x", "hash-1", deadlineEpochMs = 2000)
        gate.requestAuthorization(intent.id)
        gate.authorize(intent.id)
        gate.submit(intent.id, Acknowledgment.UNCERTAIN)

        // A second submit while uncertain is rejected — no blind retry.
        assertFalse(gate.submit(intent.id, Acknowledgment.ACKNOWLEDGED).accepted)
        gate.reconcileUncertain(intent.id)
        assertTrue(gate.submit(intent.id, Acknowledgment.ACKNOWLEDGED).accepted)
    }

    @Test
    fun duplicateActionsAreRejected() {
        val queue = JobQueue(LocalDocumentStore(createTempDirectory("smoke-duplicate")))
        queue.load()
        queue.add(job("j1"))
        try {
            queue.add(job("j1"))
            throw AssertionError("Expected duplicate job rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        val engine = RoutineEngine()
        engine.register(
            RoutineEngine.Routine("r1", "check", listOf("s1"), emptyList()),
        )
        try {
            engine.register(RoutineEngine.Routine("r1", "check", listOf("s1"), emptyList()))
            throw AssertionError("Expected duplicate routine rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
