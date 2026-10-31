package io.github.mohamtur1.fourcbon2.local

import kotlin.io.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobQueueTest {

    private fun job(
        id: String,
        state: JobState = JobState.DRAFT,
        deadlineEpochMs: Long = Long.MAX_VALUE,
        dependencies: List<String> = emptyList(),
    ) = JobRecord(
        id = id,
        ownerAccount = "owner",
        goal = "goal-$id",
        workflowVersion = 1,
        inputs = emptyMap(),
        artifactHashes = emptyMap(),
        baseRevision = "rev-1",
        resourceCaps = ResourceCaps(maxActions = 1, maxUploadBytes = 1024, maxMinutes = 1),
        dependencies = dependencies,
        deadlineEpochMs = deadlineEpochMs,
        authorizationScope = "local-only",
        state = state,
    )

    @Test
    fun addAndTransition() {
        val queue = JobQueue(LocalDocumentStore(createTempDirectory("queue-basic")))
        queue.load()
        queue.add(job("j1"))
        assertEquals(JobState.DRAFT, queue.list()[0].state)
        queue.prepareForDispatch("j1")
        assertEquals(JobState.READY, queue.list("READY")[0].state)
        queue.transition("j1", JobState.RUNNING)
        assertEquals(JobState.RUNNING, queue.list("RUNNING")[0].state)
        queue.complete("j1")
        assertEquals(JobState.COMPLETED, queue.list("COMPLETED")[0].state)
    }

    @Test
    fun duplicateJobIdIsRejected() {
        val queue = JobQueue(LocalDocumentStore(createTempDirectory("queue-dup")))
        queue.load()
        queue.add(job("j1"))
        try {
            queue.add(job("j1"))
            throw AssertionError("Expected duplicate rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun onlyReadyEligibleJobsAreDispatchable() {
        val queue = JobQueue(LocalDocumentStore(createTempDirectory("queue-dispatch")))
        queue.load()
        queue.add(job("draft"))
        queue.add(job("ready", state = JobState.READY))
        queue.add(job("expired", state = JobState.READY, deadlineEpochMs = 1))
        queue.add(job("uncertain", state = JobState.UNCERTAIN_EXTERNAL_OUTCOME))
        val ids = queue.dispatchable().map { it.id }
        assertEquals(listOf("ready"), ids)
    }

    @Test
    fun dependenciesMustBeCompleted() {
        val queue = JobQueue(LocalDocumentStore(createTempDirectory("queue-deps")))
        queue.load()
        queue.add(job("a"))
        queue.add(job("b", state = JobState.READY, dependencies = listOf("a")))
        queue.prepareForDispatch("a")
        queue.transition("a", JobState.RUNNING)
        queue.complete("a")
        assertTrue(queue.dispatchable().any { it.id == "b" })
        queue.add(job("c", state = JobState.READY, dependencies = listOf("missing")))
        assertFalse(queue.dispatchable().any { it.id == "c" })
    }

    @Test
    fun queueSurvivesRestartWithoutReplayingConsequentialWork() {
        val dir = createTempDirectory("queue-restart")
        val first = JobQueue(LocalDocumentStore(dir))
        first.load()
        first.add(job("j1"))
        first.prepareForDispatch("j1")
        first.transition("j1", JobState.RUNNING)

        // After a restart, the running job is NOT automatically re-dispatched.
        val second = JobQueue(LocalDocumentStore(dir))
        second.load()
        assertTrue(second.dispatchable().none { it.id == "j1" })
        assertEquals(JobState.RUNNING, second.list("RUNNING")[0].state)
    }

    @Test
    fun reconnectReconcilesExpiredAndKeepsUncertain() {
        val queue = JobQueue(LocalDocumentStore(createTempDirectory("queue-reconnect")))
        queue.load()
        queue.add(job("expired", deadlineEpochMs = 1))
        queue.add(job("uncertain", state = JobState.UNCERTAIN_EXTERNAL_OUTCOME))
        queue.add(job("waiting", state = JobState.WAITING_FOR_INTERNET))

        queue.reconcileOnReconnect()
        assertEquals(JobState.FAILED, queue.list("FAILED")[0].state)
        assertEquals(JobState.UNCERTAIN_EXTERNAL_OUTCOME, queue.list("UNCERTAIN_EXTERNAL_OUTCOME")[0].state)
        assertEquals(1, queue.list("WAITING_FOR_INTERNET").size)
    }
}
