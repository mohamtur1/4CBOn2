package io.github.mohamtur1.fourcbon2

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Division 47–49 verification: champion ledger, fixed promotion gate, dispatch transports, and the
 * end-to-end Daily Experiment Cycle — the v0.3 headline capability.
 */
class Batch7ChampionGateAndDailyCycleTest {
    private class Env(
        val database: FourCBOn2Database,
        val ingest: RunTelemetryIngest,
        val ledger: ChampionLedger,
        val gate: FixedPromotionGate,
        val lease: TakeoverLeaseController,
        val timeline: ActionTimelineRepository,
        val transport: ScriptedDispatchTransport,
        val cycle: DailyExperimentCycle,
    )

    private fun env(
        prefix: String,
        script: (DispatchRequest) -> DispatchOutcome = { request ->
            DispatchOutcome.Accepted(
                dispatchRef = "kaggle_v_${request.candidateDiffId}",
                channel = request.channel,
                note = "Scripted acceptance.",
            )
        },
        configured: Boolean = true,
    ): Env {
        val dir: File = Files.createTempDirectory(prefix).toFile()
        val database = FourCBOn2Database(DiskDirectoryBackend(dir))
        val ingest = RunTelemetryIngest(database)
        val ledger = ChampionLedger(database) { 900L }
        val gate = FixedPromotionGate(ledger, database)
        val lease = TakeoverLeaseController(database)
        val timeline = ActionTimelineRepository(database)
        val transport = ScriptedDispatchTransport(DispatchChannel.KAGGLE_KERNEL, configured, script)
        val router = ExperimentDispatchRouter(
            listOf(
                transport,
                ManualHandoffDispatchTransport(urlBuilder = { "https://colab.research.google.com/x" }),
            ),
        )
        val cycle = DailyExperimentCycle(
            database = database,
            ingest = ingest,
            ledger = ledger,
            gate = gate,
            dispatchRouter = router,
            leaseController = lease,
            timeline = timeline,
            diagnoser = RunDiagnoser(),
            synthesizer = CandidateDiffSynthesizer(),
        )
        return Env(database, ingest, ledger, gate, lease, timeline, transport, cycle)
    }

    private fun championJson(score: Double = 25.83, actions: Int = 500, tokens: Long = 200_000L, version: String = "v3") =
        """{"game":"ls20","score":$score,"levels":5,"actions":$actions,"tokens":$tokens,"version":"$version","status":"complete"}"""

    private fun lockChampion(env: Env): ChampionRecord {
        val ingested = env.ingest.ingestDiagnosticsJson(
            championJson(), "arc-prize-2026", "champion run", nowEpochMs = 1_000L,
        ) as TelemetryIngestOutcome.Ingested
        return env.ledger.lockInitialChampion(ingested.telemetry, ownerConfirmed = true, nowEpochMs = 1_100L)!!
    }

    // ------------------------------------------------------------------ Division 47 ----

    @Test
    fun promotionGateRequiresOwnerApprovalThenMovesChampionAndRollsBack() {
        val env = env("4cbon2_div47_promote")
        val champion = lockChampion(env)
        assertEquals("v3", champion.versionRef)

        val candidate = (env.ingest.ingestDiagnosticsJson(
            championJson(score = 25.95, actions = 50, tokens = 20_000L, version = "v4"),
            "arc-prize-2026",
            "candidate run",
            nowEpochMs = 2_000L,
        ) as TelemetryIngestOutcome.Ingested).telemetry

        val awaiting = env.gate.evaluate(candidate, approvedByOwner = false, nowEpochMs = 2_100L)
        val pending = awaiting as PromotionGateOutcome.AwaitingOwnerApproval
        assertEquals(PromotionVerdict.AWAITING_OWNER_APPROVAL, pending.report.verdict)
        assertTrue(pending.report.notes.last().contains("Eligible"))

        // No self-promotion: the champion has not moved.
        assertEquals("v3", env.ledger.activeChampion("arc-prize-2026")!!.versionRef)

        // Owner approval applies to the report the owner actually saw.
        val promoted = env.gate.applyOwnerDecision(
            reportId = pending.report.reportId,
            approvedByOwner = true,
            telemetryLookup = { id -> env.ingest.get(id) },
            nowEpochMs = 2_200L,
        ) as PromotionGateOutcome.Promoted
        assertEquals(25.95, promoted.champion.score, 1e-9)
        assertEquals("v4", env.ledger.activeChampion("arc-prize-2026")!!.versionRef)
        assertEquals(2, env.ledger.history("arc-prize-2026").size)
        assertEquals(ChampionState.SUPERSEDED, env.ledger.history("arc-prize-2026").last().state)

        // Declining a second identical report keeps the champion.
        val again = env.gate.evaluate(candidate, approvedByOwner = false, nowEpochMs = 2_300L)
        val declined = env.gate.applyOwnerDecision(
            reportId = (again as PromotionGateOutcome.Rejected).report.reportId,
            approvedByOwner = true,
            telemetryLookup = { id -> env.ingest.get(id) },
            nowEpochMs = 2_400L,
        )
        assertTrue(declined is PromotionGateOutcome.Blocked)

        // Rollback restores the previous champion.
        val restored = env.ledger.rollbackLastPromotion("arc-prize-2026", ownerConfirmed = true, nowEpochMs = 2_500L)
        assertEquals("v3", restored!!.versionRef)
        assertEquals("v3", env.ledger.activeChampion("arc-prize-2026")!!.versionRef)
    }

    @Test
    fun promotionGateRejectsRegressionsCostBlowoutsAndUnusableEvidence() {
        val env = env("4cbon2_div47_reject")
        lockChampion(env)

        // Regression → rejected, champion retained.
        val worse = (env.ingest.ingestDiagnosticsJson(
            championJson(score = 25.15, version = "v4"), "arc-prize-2026", "regressed", nowEpochMs = 3_000L,
        ) as TelemetryIngestOutcome.Ingested).telemetry
        val rejected = env.gate.evaluate(worse, nowEpochMs = 3_100L)
        assertTrue(rejected is PromotionGateOutcome.Rejected)
        assertTrue((rejected as PromotionGateOutcome.Rejected).report.notes.first().contains("did not beat"))

        // Better score but token pace far outside the cost envelope → rejected.
        val expensive = (env.ingest.ingestDiagnosticsJson(
            championJson(score = 25.99, actions = 50, tokens = 60_000L, version = "v5"),
            "arc-prize-2026", "expensive", nowEpochMs = 3_200L,
        ) as TelemetryIngestOutcome.Ingested).telemetry
        val costRejected = env.gate.evaluate(expensive, nowEpochMs = 3_300L)
        assertTrue(costRejected is PromotionGateOutcome.Rejected)
        assertTrue((costRejected as PromotionGateOutcome.Rejected).report.notes.any { it.contains("cost envelope") })

        // Too few actions → inconclusive, not a promotion.
        val tiny = (env.ingest.ingestDiagnosticsJson(
            championJson(score = 99.0, actions = 3, version = "v6"), "arc-prize-2026", "tiny", nowEpochMs = 3_400L,
        ) as TelemetryIngestOutcome.Ingested).telemetry
        assertTrue(env.gate.evaluate(tiny, nowEpochMs = 3_500L) is PromotionGateOutcome.Inconclusive)

        // No champion at all → blocked with an honest instruction.
        val otherEnv = env("4cbon2_div47_nochamp")
        val run = (otherEnv.ingest.ingestDiagnosticsJson(
            championJson(), "arc-prize-2026", "first run", nowEpochMs = 3_600L,
        ) as TelemetryIngestOutcome.Ingested).telemetry
        val blocked = otherEnv.gate.evaluate(run, nowEpochMs = 3_700L)
        assertTrue(blocked is PromotionGateOutcome.Blocked)
        assertTrue((blocked as PromotionGateOutcome.Blocked).report.notes.first().contains("No champion"))
    }

    // ------------------------------------------------------------------ Division 48/49 ----

    @Test
    fun dailyCycleClosesTheLoopFromTelemetryToChampionPromotion() {
        val env = env("4cbon2_div48_full")
        lockChampion(env)
        env.lease.startOrResumeAutomation(sessionId = "sess_daily", explicitOwnerResume = true, nowEpochMs = 1_200L)

        val opened = env.cycle.openCycle(
            "arc-prize-2026", BenchmarkKind.ARC_AGI_INTERACTIVE, DispatchChannel.KAGGLE_KERNEL, 1_300L,
        )
        val cycle = (opened as CycleOpenResult.Opened).state
        assertEquals(DailyCycleStage.INGESTED, cycle.stage)

        // 1) A bad run arrives: token burn + regression against the champion.
        val propose = env.cycle.ingestDiagnoseAndPropose(
            cycleId = cycle.cycleId,
            rawDiagnosticsJson = """{"game":"r11l","score":25.15,"levels":4,"actions":53,"tokens":96609,"version":"v4","status":"complete"}""",
            runLabel = "r11l frontier run",
            nowEpochMs = 1_400L,
        )
        val ready = propose as DailyCycleStepResult.AwaitingOwner
        assertEquals(DailyCycleStage.PROPOSED, ready.state.stage)
        assertNotNull(ready.state.candidateDiffId)
        assertTrue(ready.prompt.contains("Approve & Run"))

        val diff = env.cycle.loadDiff(ready.state.candidateDiffId)!!
        assertEquals(CandidateLever.REVERT_TO_CHAMPION, diff.lever)
        assertEquals("v3", diff.patch[BoundedPatchPolicy.KEY_REVERT_TO_VERSION])

        // 2) Owner taps Approve & Run → dispatched through the scripted Kaggle channel.
        val dispatched = env.cycle.approveAndDispatch(ready.state.cycleId, approvedByOwner = true, nowEpochMs = 1_500L)
        assertEquals(DailyCycleStage.DISPATCHED, dispatched.state.stage)
        assertEquals(1, env.transport.sentRequests.size)
        assertEquals(1, env.transport.sentRequests.first().diffPatch.size)
        assertEquals(
            DispatchIntentState.DISPATCHED,
            env.cycle.existingIntent("${ready.state.cycleId}|${diff.diffId}")!!.state,
        )
        assertNotNull(dispatched.state.dispatchRef)

        // 3) Double-tap does not re-send.
        val doubleTap = env.cycle.approveAndDispatch(ready.state.cycleId, approvedByOwner = true, nowEpochMs = 1_550L)
        assertTrue(doubleTap is DailyCycleStepResult.Advanced)
        assertEquals(1, env.transport.sentRequests.size)

        // 4) The candidate run comes back better and cheaper than the champion.
        val gated = env.cycle.recordCandidateTelemetry(
            cycleId = ready.state.cycleId,
            rawDiagnosticsJson = """{"game":"ls20","score":25.99,"levels":5,"actions":50,"tokens":20000,"version":"v5","status":"complete"}""",
            runLabel = "candidate v5",
            nowEpochMs = 1_600L,
        )
        val awaiting = gated as DailyCycleStepResult.AwaitingOwner
        assertEquals(DailyCycleStage.GATED, awaiting.state.stage)
        assertNotNull(awaiting.state.promotionReportId)
        assertEquals("v3", env.ledger.activeChampion("arc-prize-2026")!!.versionRef)

        // 5) 1-tap promotion.
        val promoted = env.cycle.resolvePromotion(ready.state.cycleId, approvedByOwner = true, nowEpochMs = 1_700L)
        assertEquals(DailyCycleStage.PROMOTED, promoted.state.stage)
        assertEquals("v5", env.ledger.activeChampion("arc-prize-2026")!!.versionRef)
        assertEquals(25.99, env.ledger.activeChampion("arc-prize-2026")!!.score, 1e-9)

        // 6) Everything is durable and auditable.
        val reloaded = env.cycle.load(ready.state.cycleId)!!
        assertEquals(DailyCycleStage.PROMOTED, reloaded.stage)
        val events = env.timeline.listEvents(jobIdFilter = ready.state.cycleId, limit = 50)
        assertTrue(events.size >= 5)
        assertTrue(events.any { it.summary.contains("Ingested telemetry") })
        assertTrue(events.any { it.summary.contains("Diagnosis") })
        assertTrue(events.any { it.summary.contains("Gate") })
        assertTrue(events.all { it.summary.isNotBlank() })

        // A finished cycle no longer blocks a new one.
        val next = env.cycle.openCycle(
            "arc-prize-2026", BenchmarkKind.ARC_AGI_INTERACTIVE, DispatchChannel.KAGGLE_KERNEL, 1_800L,
        )
        assertTrue(next is CycleOpenResult.Opened)
        assertFalse(env.cycle.activeCycle("arc-prize-2026") == null)
    }

    @Test
    fun dailyCycleHonoursTakeoverStopAndMissingConfigurationBeforeAnyExternalCall() {
        val env = env("4cbon2_div48_stop")
        lockChampion(env)

        val cycle = (env.cycle.openCycle(
            "arc-prize-2026", BenchmarkKind.ARC_AGI_INTERACTIVE, DispatchChannel.KAGGLE_KERNEL, 2_000L,
        ) as CycleOpenResult.Opened).state
        val ready = env.cycle.ingestDiagnoseAndPropose(
            cycle.cycleId,
            """{"game":"r11l","score":25.15,"levels":4,"actions":53,"tokens":96609,"version":"v4","status":"complete"}""",
            "run",
            nowEpochMs = 2_100L,
        ) as DailyCycleStepResult.AwaitingOwner

        // Takeover first: the dispatch must not reach the transport.
        env.lease.startOrResumeAutomation(sessionId = "sess_x", explicitOwnerResume = true, nowEpochMs = 2_200L)
        env.lease.takeOver(reason = "Owner took the phone.", nowEpochMs = 2_300L)

        val blocked = env.cycle.approveAndDispatch(ready.state.cycleId, approvedByOwner = true, nowEpochMs = 2_400L)
        val blockedResult = blocked as DailyCycleStepResult.Blocked
        assertEquals(DailyCycleStage.BLOCKED, blockedResult.state.stage)
        assertTrue(blockedResult.reason.contains("Control lease"))
        assertTrue(env.transport.sentRequests.isEmpty())
        assertTrue(env.timeline.listEvents(jobIdFilter = ready.state.cycleId).any { it.stage == TimelineStage.TAKEOVER_OR_STOP })

        // A dispatch channel with no credential is reported honestly, not faked.
        val unconfigured = env("4cbon2_div48_unconfigured", configured = false)
        lockChampion(unconfigured)
        unconfigured.lease.startOrResumeAutomation(sessionId = "sess_y", explicitOwnerResume = true, nowEpochMs = 2_500L)
        val cycle2 = (unconfigured.cycle.openCycle(
            "arc-prize-2026", BenchmarkKind.ARC_AGI_INTERACTIVE, DispatchChannel.KAGGLE_KERNEL, 2_600L,
        ) as CycleOpenResult.Opened).state
        val ready2 = unconfigured.cycle.ingestDiagnoseAndPropose(
            cycle2.cycleId,
            """{"game":"r11l","score":25.15,"levels":4,"actions":53,"tokens":96609,"version":"v4","status":"complete"}""",
            "run",
            nowEpochMs = 2_700L,
        ) as DailyCycleStepResult.AwaitingOwner
        val notConfigured = unconfigured.cycle.approveAndDispatch(
            ready2.state.cycleId, approvedByOwner = true, nowEpochMs = 2_800L,
        ) as DailyCycleStepResult.Blocked
        assertEquals(DailyCycleStage.BLOCKED, notConfigured.state.stage)
        assertTrue(notConfigured.reason.contains("not configured"))
        assertFalse(unconfigured.cycle.dispatchReadiness().getValue(DispatchChannel.KAGGLE_KERNEL))
    }

    @Test
    fun uncertainDispatchIsReconciledBeforeAnyRetryAndSurvivesRestart() {
        val uncertainEnv = env(
            prefix = "4cbon2_div48_uncertain",
            script = { DispatchOutcome.UncertainExternalOutcome("Connection dropped after POST.") },
        )
        lockChampion(uncertainEnv)
        uncertainEnv.lease.startOrResumeAutomation(sessionId = "sess_z", explicitOwnerResume = true, nowEpochMs = 3_000L)
        val cycle = (uncertainEnv.cycle.openCycle(
            "arc-prize-2026", BenchmarkKind.ARC_AGI_INTERACTIVE, DispatchChannel.KAGGLE_KERNEL, 3_100L,
        ) as CycleOpenResult.Opened).state
        val ready = uncertainEnv.cycle.ingestDiagnoseAndPropose(
            cycle.cycleId,
            """{"game":"r11l","score":25.15,"levels":4,"actions":53,"tokens":96609,"version":"v4","status":"complete"}""",
            "run",
            nowEpochMs = 3_200L,
        ) as DailyCycleStepResult.AwaitingOwner

        val uncertain = uncertainEnv.cycle.approveAndDispatch(
            ready.state.cycleId, approvedByOwner = true, nowEpochMs = 3_300L,
        )
        assertEquals(DailyCycleStage.AWAITING_RECONCILIATION, uncertain.state.stage)

        // Retry is refused while the outcome is unknown; nothing is re-sent.
        val retry = uncertainEnv.cycle.approveAndDispatch(
            ready.state.cycleId, approvedByOwner = true, nowEpochMs = 3_400L,
        )
        assertTrue(retry is DailyCycleStepResult.Blocked)
        assertEquals(1, uncertainEnv.transport.sentRequests.size)

        // State survives a restart (fresh objects over the same database file).
        val restarted = DailyExperimentCycle(
            database = uncertainEnv.database,
            ingest = uncertainEnv.ingest,
            ledger = uncertainEnv.ledger,
            gate = uncertainEnv.gate,
            dispatchRouter = ExperimentDispatchRouter(listOf(uncertainEnv.transport)),
            leaseController = uncertainEnv.lease,
            timeline = uncertainEnv.timeline,
        )
        assertEquals(DailyCycleStage.AWAITING_RECONCILIATION, restarted.load(ready.state.cycleId)!!.stage)

        // Reconcile → the run did land, so the cycle continues without a second send.
        val reconciled = restarted.reconcileDispatch(
            ready.state.cycleId, landed = true, dispatchRef = "kaggle_v_known", nowEpochMs = 3_500L,
        )
        assertEquals(DailyCycleStage.DISPATCHED, reconciled.state.stage)
        assertEquals(1, uncertainEnv.transport.sentRequests.size)

        // And the loop can still end honestly with a rejection.
        val gated = restarted.recordCandidateTelemetry(
            ready.state.cycleId,
            """{"game":"ls20","score":25.20,"levels":5,"actions":60,"tokens":24000,"version":"v5","status":"complete"}""",
            "candidate weaker",
            nowEpochMs = 3_600L,
        )
        assertEquals(DailyCycleStage.REJECTED, gated.state.stage)
        assertEquals("v3", uncertainEnv.ledger.activeChampion("arc-prize-2026")!!.versionRef)
        assertNull(restarted.load(ready.state.cycleId)!!.championId?.takeIf { it == "missing" })
    }

    @Test
    fun noActionCycleEndsHonestlyWhenTheRunMatchesTheChampion() {
        val env = env("4cbon2_div48_stable")
        lockChampion(env)
        env.lease.startOrResumeAutomation(sessionId = "sess_s", explicitOwnerResume = true, nowEpochMs = 4_000L)
        val cycle = (env.cycle.openCycle(
            "arc-prize-2026", BenchmarkKind.ARC_AGI_INTERACTIVE, DispatchChannel.COLAB_MANUAL, 4_100L,
        ) as CycleOpenResult.Opened).state

        val result = env.cycle.ingestDiagnoseAndPropose(
            cycle.cycleId,
            championJson(score = 25.84, actions = 500, tokens = 200_000L, version = "v3b"),
            "repeat of champion",
            nowEpochMs = 4_200L,
        )

        val advanced = result as DailyCycleStepResult.Advanced
        assertEquals(DailyCycleStage.NO_ACTION_NEEDED, advanced.state.stage)
        assertTrue(advanced.note.contains("does not justify") || advanced.note.contains("Nothing to fix"))
        assertTrue(env.transport.sentRequests.isEmpty())
        assertNotNull(env.cycle.loadDiagnosis(advanced.state.diagnosisReportId))
    }
}
