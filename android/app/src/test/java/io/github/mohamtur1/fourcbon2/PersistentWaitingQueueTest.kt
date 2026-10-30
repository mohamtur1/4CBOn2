package io.github.mohamtur1.fourcbon2

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistentWaitingQueueTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private var now = 1_700_000_000_000L

    private fun databaseFile(): File = File(temporaryFolder.newFolder(), "queue.db")

    private fun openQueue(file: File): Pair<LocalEntityDatabase, PersistentWaitingQueue> {
        val database = LocalEntityDatabase(file)
        database.open()
        return database to PersistentWaitingQueue(database) { now }
    }

    @Test
    fun queuedJobSurvivesRestartAndIsReportedAsRecovered() {
        val file = databaseFile()

        val (firstDatabase, firstQueue) = openQueue(file)
        val stored = firstQueue.enqueue("job-1", "Review the synthetic report")
        assertEquals(QueueOutcome.Stored(firstQueue.job("job-1")!!), stored)
        // Queued in this session, so nothing has been carried over yet.
        assertEquals(0, firstQueue.recoveredCount())
        firstDatabase.close()

        now += 60_000L
        val (secondDatabase, secondQueue) = openQueue(file)
        try {
            assertEquals(1, secondQueue.recoveredCount())
            assertEquals(listOf("job-1"), secondQueue.recovered().map { it.id })
            assertEquals("Review the synthetic report", secondQueue.job("job-1")?.summary)
            // Recovered means "still waiting", never "already handled".
            assertEquals(QueuedJobState.WAITING, secondQueue.job("job-1")?.state)
        } finally {
            secondDatabase.close()
        }
    }

    @Test
    fun constructingTheQueueOverAnExistingFileWritesNothing() {
        val file = databaseFile()
        val (database, queue) = openQueue(file)
        queue.enqueue("job-1", "First")
        queue.enqueue("job-2", "Second")

        val before = file.readText(Charsets.UTF_8)
        now += 5_000L

        // A second queue over the same open database must only read.
        val reopened = PersistentWaitingQueue(database) { now }
        assertEquals(2, reopened.recoveredCount())
        assertEquals(2, reopened.active().size)
        assertEquals(before, file.readText(Charsets.UTF_8))

        database.close()
    }

    @Test
    fun acknowledgingRecoveredJobClearsItWithoutRunningAnything() {
        val file = databaseFile()
        val (firstDatabase, firstQueue) = openQueue(file)
        firstQueue.enqueue("job-1", "Deferred network refresh", DeferralReason.NETWORK_UNAVAILABLE)
        val beforeRestart = firstQueue.job("job-1")!!
        firstDatabase.close()

        now += 60_000L
        val (secondDatabase, secondQueue) = openQueue(file)
        try {
            assertEquals(1, secondQueue.recoveredCount())

            val outcome = secondQueue.acknowledgeRecovered("job-1")
            assertTrue("acknowledgement must be accepted", outcome is QueueOutcome.Stored)

            assertEquals(0, secondQueue.recoveredCount())
            val after = secondQueue.job("job-1")!!
            // The owner's attention moved forward; nothing else about the job changed.
            assertTrue(after.lastOwnerActionAtEpochMillis >= secondQueue.sessionStartedAtEpochMillis)
            assertEquals(beforeRestart.state, after.state)
            assertEquals(beforeRestart.deferral, after.deferral)
            assertEquals(beforeRestart.queuedAtEpochMillis, after.queuedAtEpochMillis)
            assertEquals(beforeRestart.summary, after.summary)
            // It is still waiting. Acknowledgement is not execution.
            assertEquals(1, secondQueue.active().size)
        } finally {
            secondDatabase.close()
        }
    }

    @Test
    fun acknowledgingAJobQueuedThisSessionIsRefused() {
        val (database, queue) = openQueue(databaseFile())
        try {
            queue.enqueue("job-1", "Fresh")
            assertEquals(
                QueueOutcome.Refused("job-1", QueueRefusal.NOT_A_RECOVERED_JOB),
                queue.acknowledgeRecovered("job-1"),
            )
            assertEquals(
                QueueOutcome.Refused("missing", QueueRefusal.UNKNOWN_JOB),
                queue.acknowledgeRecovered("missing"),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun cancellationIsTerminalAndKeepsTheRecord() {
        val (database, queue) = openQueue(databaseFile())
        try {
            queue.enqueue("job-1", "Will be cancelled", DeferralReason.AWAITING_PERMISSION)

            val cancelled = queue.cancel("job-1")
            assertTrue(cancelled is QueueOutcome.Stored)
            val job = (cancelled as QueueOutcome.Stored).job
            assertEquals(QueuedJobState.CANCELLED, job.state)
            // A terminal job holds no deferral, so it cannot be mistaken for pending work.
            assertNull(job.deferral)

            assertEquals(QueueOutcome.Refused("job-1", QueueRefusal.JOB_TERMINAL), queue.cancel("job-1"))
            assertEquals(QueueOutcome.Refused("job-1", QueueRefusal.JOB_TERMINAL), queue.defer("job-1", DeferralReason.NETWORK_UNAVAILABLE))

            // Dropped from the active queue but kept in the full record.
            assertEquals(emptyList<LocalQueuedJob>(), queue.active())
            assertEquals(1, queue.all().size)
            assertEquals(0, queue.recoveredCount())

            assertEquals(QueueOutcome.Refused("missing", QueueRefusal.UNKNOWN_JOB), queue.cancel("missing"))
        } finally {
            database.close()
        }
    }

    @Test
    fun cancellationSurvivesRestartAndNeverBecomesActive() {
        val file = databaseFile()
        val (firstDatabase, firstQueue) = openQueue(file)
        firstQueue.enqueue("job-1", "Cancelled before restart")
        firstQueue.cancel("job-1")
        firstDatabase.close()

        now += 60_000L
        val (secondDatabase, secondQueue) = openQueue(file)
        try {
            assertEquals(emptyList<LocalQueuedJob>(), secondQueue.active())
            assertEquals(0, secondQueue.recoveredCount())
            assertEquals(QueuedJobState.CANCELLED, secondQueue.job("job-1")?.state)
        } finally {
            secondDatabase.close()
        }
    }

    @Test
    fun deferringRecordsTheReasonWithoutClaimingAnAttempt() {
        val (database, queue) = openQueue(databaseFile())
        try {
            queue.enqueue("net-1", "Refresh the synthetic source")

            val outcome = queue.defer("net-1", DeferralReason.NETWORK_UNAVAILABLE)
            assertTrue(outcome is QueueOutcome.Stored)
            val job = (outcome as QueueOutcome.Stored).job

            assertEquals(DeferralReason.NETWORK_UNAVAILABLE, job.deferral)
            // Still waiting: deferred is a form of waiting, not an outcome.
            assertEquals(QueuedJobState.WAITING, job.state)
            assertEquals(1, queue.active().size)

            // Replacing the reason is allowed and keeps the job waiting.
            val redeferred = queue.defer("net-1", DeferralReason.AWAITING_OWNER_DECISION)
            assertEquals(
                DeferralReason.AWAITING_OWNER_DECISION,
                (redeferred as QueueOutcome.Stored).job.deferral,
            )

            assertEquals(QueueOutcome.Refused("missing", QueueRefusal.UNKNOWN_JOB), queue.defer("missing", DeferralReason.AWAITING_PERMISSION))
        } finally {
            database.close()
        }
    }

    @Test
    fun deferredNetworkJobSurvivesRestartStillDeferred() {
        val file = databaseFile()
        val (firstDatabase, firstQueue) = openQueue(file)
        firstQueue.enqueue("net-1", "Refresh the synthetic source")
        firstQueue.defer("net-1", DeferralReason.NETWORK_UNAVAILABLE)
        firstDatabase.close()

        now += 60_000L
        val (secondDatabase, secondQueue) = openQueue(file)
        try {
            val job = secondQueue.job("net-1")!!
            assertEquals(DeferralReason.NETWORK_UNAVAILABLE, job.deferral)
            assertEquals(QueuedJobState.WAITING, job.state)
            assertEquals(1, secondQueue.recoveredCount())
        } finally {
            secondDatabase.close()
        }
    }

    @Test
    fun duplicateIdentifiersAreRefusedEvenAfterCancellation() {
        val (database, queue) = openQueue(databaseFile())
        try {
            queue.enqueue("job-1", "First")
            assertEquals(
                QueueOutcome.Refused("job-1", QueueRefusal.DUPLICATE_JOB_ID),
                queue.enqueue("job-1", "Second"),
            )
            assertEquals("First", queue.job("job-1")?.summary)

            queue.cancel("job-1")
            // A cancellation keeps its history; a new job cannot silently inherit the id.
            assertEquals(
                QueueOutcome.Refused("job-1", QueueRefusal.DUPLICATE_JOB_ID),
                queue.enqueue("job-1", "Third"),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun aFullQueueRefusesNewJobsInsteadOfDroppingThem() {
        val (database, queue) = openQueue(databaseFile())
        try {
            repeat(QueueBounds.MAX_ACTIVE_JOBS) { index ->
                val outcome = queue.enqueue("job-$index", "Queued entry $index")
                assertTrue("entry $index should be stored", outcome is QueueOutcome.Stored)
            }
            assertEquals(QueueBounds.MAX_ACTIVE_JOBS, queue.active().size)

            val refused = queue.enqueue("overflow", "One too many")
            assertEquals(
                QueueOutcome.Refused("overflow", QueueRefusal.QUEUE_FULL),
                refused,
            )
            // Nothing was silently dropped to make room.
            assertEquals(QueueBounds.MAX_ACTIVE_JOBS, queue.active().size)
            assertNull(queue.job("overflow"))

            // Cancelling frees a slot, so the cap is a live limit rather than a dead end.
            queue.cancel("job-0")
            assertTrue(queue.enqueue("overflow", "Now it fits") is QueueOutcome.Stored)
        } finally {
            database.close()
        }
    }

    @Test
    fun activeQueueIsOrderedByQueuedTimeThenIdentifier() {
        val (database, queue) = openQueue(databaseFile())
        try {
            now = 1_700_000_000_300L
            queue.enqueue("b", "Second by time")
            now = 1_700_000_000_100L
            queue.enqueue("c", "First by time")
            queue.enqueue("a", "Same time, earlier id")

            // "c" and "a" share a queued time, so the identifier tie-break puts "a" first.
            assertEquals(listOf("a", "c", "b"), queue.active().map { it.id })
            // Order is stable across repeated reads.
            assertEquals(queue.active().map { it.id }, queue.active().map { it.id })
            assertEquals(queue.all().map { it.id }, queue.active().map { it.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun queueStaysInertWithRespectToRegistryScopeAndLease() {
        val (database, queue) = openQueue(databaseFile())
        try {
            val registry = SelectedAppCapabilityRegistry(ActionRouteProbe.NONE)
            val lease = ActionLease(createdAtEpochMillis = now, nowEpochMillis = { now })
            lease.apply(
                LeaseCommand(LeaseCommandKind.STOP, LeaseCommandOrigin.OWNER_LOCAL, epoch = 1, issuedAtEpochMillis = now),
            )

            queue.enqueue("job-1", "Something the owner wants later")
            queue.defer("job-1", DeferralReason.NETWORK_UNAVAILABLE)
            queue.active()
            queue.recovered()
            queue.all()

            assertFalse(registry.isSelected("org.kaggle.app"))
            assertEquals(
                CapabilityDecision.Unavailable("org.kaggle.app", "open", UnavailableReason.APP_NOT_SELECTED),
                registry.resolve("org.kaggle.app", "open"),
            )
            assertEquals(LeaseState.STOPPED, lease.state)
            assertEquals(LeaseRefusal.LEASE_TERMINATED, lease.stepRefusal(now))
        } finally {
            database.close()
        }
    }

    @Test
    fun queueOffersNoWayToExecuteAnything() {
        val forbidden = listOf(
            "run", "execute", "exec", "start", "launch", "dispatch", "perform",
            "invoke", "apply", "send", "fire", "replay", "resume", "trigger", "do",
        )
        val methodNames = PersistentWaitingQueue::class.java.declaredMethods.map { it.name.lowercase() }
        assertTrue("expected the queue to expose methods", methodNames.isNotEmpty())
        forbidden.forEach { word ->
            assertFalse(
                "the queue must not expose a method named $word",
                methodNames.contains(word),
            )
        }

        // No method may accept or return a boundary handle that could act on the owner's behalf.
        PersistentWaitingQueue::class.java.declaredMethods.forEach { method ->
            (method.parameterTypes.toList() + method.returnType).forEach { type ->
                val name = type.name
                listOf("Registry", "Lease", "ScopeContract", "ScopeEnforcer", "Intent", "Socket", "URL")
                    .forEach { boundary ->
                        assertFalse("unexpected boundary type $name in ${method.name}", name.contains(boundary))
                    }
            }
        }
    }

    @Test
    fun recoveryIsDerivedFromTheClockSoTheSameJobCanRecoverTwice() {
        val file = databaseFile()
        val (firstDatabase, firstQueue) = openQueue(file)
        firstQueue.enqueue("job-1", "Kept across two restarts")
        firstDatabase.close()

        // First restart: recovered, acknowledged.
        now += 60_000L
        val (secondDatabase, secondQueue) = openQueue(file)
        assertEquals(1, secondQueue.recoveredCount())
        secondQueue.acknowledgeRecovered("job-1")
        assertEquals(0, secondQueue.recoveredCount())
        secondDatabase.close()

        // Second restart with the clock not advanced past the acknowledgement: still owned.
        val (thirdDatabase, thirdQueue) = openQueue(file)
        try {
            assertEquals(QueuedJobState.WAITING, thirdQueue.job("job-1")?.state)
            assertEquals(1, thirdQueue.active().size)

            // Advancing the clock past the last owner action recovers it again, because
            // recovery is a comparison rather than a stored flag.
            now += 60_000L
            val (fourthDatabase, fourthQueue) = openQueue(file)
            try {
                assertEquals(1, fourthQueue.recoveredCount())
            } finally {
                fourthDatabase.close()
            }
        } finally {
            thirdDatabase.close()
        }
    }

    @Test
    fun queueRowsRoundTripThroughTheDatabaseInTheirOwnEntityType() {
        val file = databaseFile()
        val (database, queue) = openQueue(file)
        queue.enqueue("job-1", "Alpha", DeferralReason.AWAITING_PERMISSION)
        queue.enqueue("job-2", "Beta")
        queue.cancel("job-2")

        val rows = file.readLines(Charsets.UTF_8).drop(1).filter { it.isNotEmpty() }
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.startsWith("QUEUED_JOB\t") })
        // Seven columns: type, id, summary, state, deferral, queuedAt, lastOwnerActionAt.
        assertTrue(rows.all { it.split('\t').size == 7 })
        // The free-text summary is never stored in cleartext.
        assertFalse(file.readText(Charsets.UTF_8).contains("Alpha"))
        // The deferral sentinel is used for a job with no deferral.
        assertTrue(rows.any { it.split('\t')[4] == "-" })
        assertTrue(rows.any { it.split('\t')[4] == "AWAITING_PERMISSION" })

        database.close()
    }

    @Test
    fun queueRejectsAnOversizedOrMalformedSummary() {
        val (database, queue) = openQueue(databaseFile())
        try {
            assertThrows(IllegalArgumentException::class.java) {
                queue.enqueue("job-1", "a".repeat(QueueBounds.MAX_SUMMARY_LENGTH + 1))
            }
            assertThrows(IllegalArgumentException::class.java) { queue.enqueue("job-1", " ") }
            assertThrows(IllegalArgumentException::class.java) { queue.enqueue("bad id", "Valid summary") }
            // Nothing was written by a rejected attempt.
            assertEquals(emptyList<LocalQueuedJob>(), queue.all())
        } finally {
            database.close()
        }
    }

    @Test
    fun queueSourceOpensNoSocketAndTouchesNoFilesystemPath() {
        val source = File(
            "android/app/src/main/java/io/github/mohamtur1/fourcbon2/PersistentWaitingQueue.kt",
        )
        if (!source.exists()) return // Source layout differs outside the repository; CI covers it.

        val text = source.readText(Charsets.UTF_8)
        listOf("Socket", "URL(", "okhttp", "HttpClient", "java.net", "java.io.File", "RandomAccessFile")
            .forEach { forbidden ->
                assertFalse("queue source must not reference $forbidden", text.contains(forbidden))
            }
    }
}
