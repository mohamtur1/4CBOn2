package io.github.mohamtur1.fourcbon2.integration

import io.github.mohamtur1.fourcbon2.action.IntentActionRunner
import io.github.mohamtur1.fourcbon2.action.SupportedAppAction
import io.github.mohamtur1.fourcbon2.capability.AppActionKind
import io.github.mohamtur1.fourcbon2.capability.AppCapability
import io.github.mohamtur1.fourcbon2.capability.AppCapabilityRegistry
import io.github.mohamtur1.fourcbon2.capability.SelectedApp
import io.github.mohamtur1.fourcbon2.control.TakeoverLease
import io.github.mohamtur1.fourcbon2.guard.CriticalActionGuard
import io.github.mohamtur1.fourcbon2.local.JobQueue
import io.github.mohamtur1.fourcbon2.local.LocalDocumentStore
import io.github.mohamtur1.fourcbon2.local.JobState
import io.github.mohamtur1.fourcbon2.local.JobRecord
import io.github.mohamtur1.fourcbon2.local.ResourceCaps
import io.github.mohamtur1.fourcbon2.scope.ScopeContract
import io.github.mohamtur1.fourcbon2.scope.ScopeLimits
import io.github.mohamtur1.fourcbon2.submission.SubmissionGate
import io.github.mohamtur1.fourcbon2.submission.SubmissionGate.Acknowledgment
import kotlin.io.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Harmless cross-app integration test (Division 40).
 *
 * A few selected supported workflows with synthetic data: open a selected app,
 * queue a job, run a sensitive action through the guard, and submit through
 * the gate. Scope escape, takeover, and manual handoff are exercised too.
 */
class CrossAppIntegrationTest {

    private val notes = SelectedApp(packageName = "com.example.notes", displayName = "Notes")
    private val bank = SelectedApp(packageName = "com.example.bank", displayName = "Bank")

    @Test
    fun selectedWorkflowOpensSelectedApp() {
        val registry = AppCapabilityRegistry()
        registry.register(AppCapability(notes, AppActionKind.OPEN_APP, supported = true, reason = ""))
        val runner = IntentActionRunner(registry)
        val outcome = runner.run(SupportedAppAction(notes, AppActionKind.OPEN_APP, "Open notes", requiresApproval = false))
        assertEquals(IntentActionRunner.ActionResult.DELIVERED, outcome.result)
    }

    @Test
    fun unselectedAppCannotBeOpened() {
        val registry = AppCapabilityRegistry()
        registry.register(AppCapability(notes, AppActionKind.OPEN_APP, supported = true, reason = ""))
        val runner = IntentActionRunner(registry)
        val outcome = runner.run(SupportedAppAction(bank, AppActionKind.OPEN_APP, "Open bank", requiresApproval = false))
        assertEquals(IntentActionRunner.ActionResult.UNSUPPORTED, outcome.result)
    }

    @Test
    fun queuedJobThenApprovalThenSubmission() {
        var now = 1000L
        val queue = JobQueue(LocalDocumentStore(createTempDirectory("integration-queue")))
        queue.load()
        queue.add(
            JobRecord(
                id = "j1",
                ownerAccount = "owner",
                goal = "submit report",
                workflowVersion = 1,
                inputs = emptyMap(),
                artifactHashes = mapOf("report.csv" to "hash-1"),
                baseRevision = "rev-1",
                resourceCaps = ResourceCaps(maxActions = 1, maxUploadBytes = 1024, maxMinutes = 1),
                dependencies = emptyList(),
                deadlineEpochMs = Long.MAX_VALUE,
                authorizationScope = "local-only",
                state = JobState.DRAFT,
            ),
        )
        queue.prepareForDispatch("j1")
        assertTrue(queue.dispatchable().any { it.id == "j1" })
        queue.transition("j1", JobState.COMPLETED)

        val gate = SubmissionGate(clock = { now })
        val intent = gate.saveIntent("competition-x", "hash-1", deadlineEpochMs = 2000)
        gate.requestAuthorization(intent.id)
        gate.authorize(intent.id)
        val outcome = gate.submit(intent.id, Acknowledgment.ACKNOWLEDGED)
        assertTrue(outcome.accepted)
    }

    @Test
    fun takeoverStopsTheWorkflowAndBlocksStaleCommands() {
        val lease = TakeoverLease()
        lease.beginControl("owner")
        lease.takeOver("owner")
        assertFalse(lease.acceptCommand(commandEpoch = lease.currentEpoch(), commandOwner = "owner"))
    }

    @Test
    fun scopeEscapeIsDenied() {
        val contract = ScopeContract(
            ScopeLimits(
                allowedDestinations = listOf("com.example.notes"),
                maxActions = 1,
                expiryEpochMs = Long.MAX_VALUE,
                allowCloudUpload = false,
            ),
        )
        assertTrue(contract.beginAction("com.example.notes", "open"))
        assertFalse(contract.beginAction("com.example.bank", "open"))
    }

    @Test
    fun sensitiveWorkflowRequiresManualHandoff() {
        val guard = CriticalActionGuard()
        val preview = CriticalActionGuard.Preview("Transfer funds", "com.example.bank", "hash-1")
        val decision = guard.evaluate(preview, CriticalActionGuard.SensitiveContext.PAYMENT, accountVerified = true, approvalGranted = true)
        assertEquals(CriticalActionGuard.Decision.MANUAL_ONLY, decision.decision)
    }
}
