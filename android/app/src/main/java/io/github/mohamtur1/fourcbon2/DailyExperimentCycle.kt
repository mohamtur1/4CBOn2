package io.github.mohamtur1.fourcbon2

import java.util.UUID

/**
 * Division 48 (v0.3) — The Daily Experiment Cycle.
 *
 * This is the gap the v0.2 handoff names directly: 4CBOn2 could build a notebook and open it, but it
 * could not *run its own research day*. The cycle is a durable, resumable state machine that closes
 * the loop end to end:
 *
 * ```
 *   owner opens a cycle
 *        ↓ ingest latest run diagnostics (Division 43)
 *        ↓ diagnose against the locked champion (Division 45)
 *        ↓ synthesise ONE bounded candidate diff (Division 46)
 *        ↓ owner taps "Approve & Run" → dispatch (Division 49)
 *        ↓ ingest the candidate run's telemetry
 *        ↓ fixed promotion gate (Division 47) → 1-tap owner promotion
 *   champion moves, or the baseline is kept
 * ```
 *
 * Invariants (all covered by tests):
 *  - **One live cycle per competition.** Opening a second cycle while one is mid-flight returns the
 *    existing cycle rather than creating parallel experiments.
 *  - **Durable before dangerous.** The dispatch intent is written to disk *before* the transport call,
 *    with an idempotency key, so a restart or a dropped connection can never double-submit.
 *  - **Stop is honoured everywhere.** Every mutating step re-reads the control lease; a taken-over or
 *    stopped lease blocks dispatch immediately, not at the next scheduled tick.
 *  - **No self-promotion.** The only path to [DailyCycleStage.PROMOTED] is
 *    [resolvePromotion] with `approvedByOwner = true`.
 */

enum class DailyCycleStage(val label: String, val isTerminal: Boolean = false) {
    PROPOSED("Candidate ready for approval"),
    DIAGNOSED("Run diagnosed"),
    INGESTED("Run ingested"),
    AWAITING_OWNER_APPROVAL("Candidate dispatched — awaiting telemetry"),
    DISPATCHED("Dispatched — awaiting telemetry"),
    AWAITING_RECONCILIATION("Dispatch uncertain — reconcile first"),
    GATED("Gate decided — awaiting owner"),
    PROMOTED("Champion promoted", isTerminal = true),
    REJECTED("Rejected — champion retained", isTerminal = true),
    INCONCLUSIVE("Inconclusive — champion retained", isTerminal = true),
    NO_ACTION_NEEDED("No action needed", isTerminal = true),
    STOPPED("Stopped by owner", isTerminal = true),
    BLOCKED("Blocked — needs owner attention", isTerminal = true),
}

enum class DispatchIntentState(val label: String) {
    PREPARED("Prepared (not sent)"),
    DISPATCHED("Dispatched"),
    UNCERTAIN("Uncertain — reconcile"),
    REJECTED("Rejected by runner"),
    NOT_CONFIGURED("Not configured on device"),
}

/** Durable pre-send record. Written before the transport is called, never after. */
data class DispatchIntent(
    val intentKey: String,
    val cycleId: String,
    val channel: DispatchChannel,
    val candidateDiffId: String,
    val state: DispatchIntentState,
    val dispatchRef: String?,
    val note: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "intentKey" to JsonValue.Str(intentKey),
            "cycleId" to JsonValue.Str(cycleId),
            "channel" to JsonValue.Str(channel.name),
            "candidateDiffId" to JsonValue.Str(candidateDiffId),
            "state" to JsonValue.Str(state.name),
            "dispatchRef" to (dispatchRef?.let(JsonValue::Str) ?: JsonValue.Null),
            "note" to JsonValue.Str(note),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
            "updatedAtEpochMs" to JsonValue.Num(updatedAtEpochMs.toDouble(), updatedAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): DispatchIntent = DispatchIntent(
            intentKey = obj.stringOrDefault("intentKey"),
            cycleId = obj.stringOrDefault("cycleId"),
            channel = runCatching { DispatchChannel.valueOf(obj.stringOrDefault("channel")) }
                .getOrDefault(DispatchChannel.COLAB_MANUAL),
            candidateDiffId = obj.stringOrDefault("candidateDiffId"),
            state = runCatching { DispatchIntentState.valueOf(obj.stringOrDefault("state")) }
                .getOrDefault(DispatchIntentState.PREPARED),
            dispatchRef = obj.string("dispatchRef"),
            note = obj.stringOrDefault("note"),
            createdAtEpochMs = obj.long("createdAtEpochMs", 0L),
            updatedAtEpochMs = obj.long("updatedAtEpochMs", 0L),
        )
    }
}

data class DailyCycleState(
    val cycleId: String,
    val competitionSlug: String,
    val benchmarkKind: BenchmarkKind,
    val channel: DispatchChannel,
    val stage: DailyCycleStage,
    val telemetryId: String?,
    val diagnosisReportId: String?,
    val candidateDiffId: String?,
    val dispatchRef: String?,
    val promotionReportId: String?,
    val championId: String?,
    val note: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    /** The single thing the owner can do next, phrased for the Daily Lab card. */
    fun nextOwnerAction(): String = when (stage) {
        DailyCycleStage.PROPOSED -> "Review the candidate diff, then tap Approve & Run."
        DailyCycleStage.INGESTED, DailyCycleStage.DIAGNOSED -> "Finish diagnosis, then review the candidate."
        DailyCycleStage.DISPATCHED, DailyCycleStage.AWAITING_OWNER_APPROVAL -> "Run dispatched — waiting for its telemetry."
        DailyCycleStage.AWAITING_RECONCILIATION -> "Check the runner, then reconcile the dispatch outcome."
        DailyCycleStage.GATED -> "Review the gate report, then approve or decline promotion."
        DailyCycleStage.BLOCKED -> "Resolve the blocker, then reopen the cycle."
        DailyCycleStage.PROMOTED -> "Champion promoted. Next cycle can start."
        DailyCycleStage.REJECTED, DailyCycleStage.INCONCLUSIVE -> "Champion retained. Diagnostics are saved for the next attempt."
        DailyCycleStage.NO_ACTION_NEEDED -> "Nothing to improve today — the run matched the champion."
        DailyCycleStage.STOPPED -> "Cycle stopped by owner."
    }

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "cycleId" to JsonValue.Str(cycleId),
            "competitionSlug" to JsonValue.Str(competitionSlug),
            "benchmarkKind" to JsonValue.Str(benchmarkKind.name),
            "channel" to JsonValue.Str(channel.name),
            "stage" to JsonValue.Str(stage.name),
            "telemetryId" to (telemetryId?.let(JsonValue::Str) ?: JsonValue.Null),
            "diagnosisReportId" to (diagnosisReportId?.let(JsonValue::Str) ?: JsonValue.Null),
            "candidateDiffId" to (candidateDiffId?.let(JsonValue::Str) ?: JsonValue.Null),
            "dispatchRef" to (dispatchRef?.let(JsonValue::Str) ?: JsonValue.Null),
            "promotionReportId" to (promotionReportId?.let(JsonValue::Str) ?: JsonValue.Null),
            "championId" to (championId?.let(JsonValue::Str) ?: JsonValue.Null),
            "note" to JsonValue.Str(note),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
            "updatedAtEpochMs" to JsonValue.Num(updatedAtEpochMs.toDouble(), updatedAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): DailyCycleState = DailyCycleState(
            cycleId = obj.stringOrDefault("cycleId"),
            competitionSlug = obj.stringOrDefault("competitionSlug"),
            benchmarkKind = runCatching { BenchmarkKind.valueOf(obj.stringOrDefault("benchmarkKind")) }
                .getOrDefault(BenchmarkKind.ARC_AGI_INTERACTIVE),
            channel = runCatching { DispatchChannel.valueOf(obj.stringOrDefault("channel")) }
                .getOrDefault(DispatchChannel.COLAB_MANUAL),
            stage = runCatching { DailyCycleStage.valueOf(obj.stringOrDefault("stage")) }
                .getOrDefault(DailyCycleStage.BLOCKED),
            telemetryId = obj.string("telemetryId"),
            diagnosisReportId = obj.string("diagnosisReportId"),
            candidateDiffId = obj.string("candidateDiffId"),
            dispatchRef = obj.string("dispatchRef"),
            promotionReportId = obj.string("promotionReportId"),
            championId = obj.string("championId"),
            note = obj.stringOrDefault("note"),
            createdAtEpochMs = obj.long("createdAtEpochMs", 0L),
            updatedAtEpochMs = obj.long("updatedAtEpochMs", 0L),
        )
    }
}

sealed interface CycleOpenResult {
    data class Opened(val state: DailyCycleState) : CycleOpenResult

    data class AlreadyRunning(val state: DailyCycleState) : CycleOpenResult

    data class Refused(val reason: String) : CycleOpenResult
}

sealed interface DailyCycleStepResult {
    val state: DailyCycleState

    data class Advanced(override val state: DailyCycleState, val note: String) : DailyCycleStepResult

    data class AwaitingOwner(override val state: DailyCycleState, val prompt: String) : DailyCycleStepResult

    data class Blocked(override val state: DailyCycleState, val reason: String) : DailyCycleStepResult
}

class DailyExperimentCycle(
    private val database: FourCBOn2Database,
    private val ingest: RunTelemetryIngest,
    private val ledger: ChampionLedger,
    private val gate: FixedPromotionGate,
    private val dispatchRouter: ExperimentDispatchRouter,
    private val leaseController: TakeoverLeaseController,
    private val timeline: ActionTimelineRepository,
    private val diagnoser: RunDiagnoser = RunDiagnoser(),
    private val synthesizer: CandidateDiffSynthesizer = CandidateDiffSynthesizer(),
    private val thresholds: DiagnosisThresholds = DiagnosisThresholds.DEFAULT,
    private val cycleIdFactory: () -> String = { "cyc_${UUID.randomUUID().toString().take(8)}" },
) {
    // ---------------------------------------------------------------- lifecycle ----

    /** Opens a cycle, or returns the one already in flight for this competition. */
    fun openCycle(
        competitionSlug: String,
        benchmarkKind: BenchmarkKind,
        channel: DispatchChannel,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CycleOpenResult {
        if (competitionSlug.isBlank()) return CycleOpenResult.Refused("A competition slug is required.")
        activeCycle(competitionSlug)?.let { existing ->
            return CycleOpenResult.AlreadyRunning(existing)
        }
        val state = DailyCycleState(
            cycleId = cycleIdFactory(),
            competitionSlug = competitionSlug.trim(),
            benchmarkKind = benchmarkKind,
            channel = channel,
            stage = DailyCycleStage.INGESTED,
            telemetryId = null,
            diagnosisReportId = null,
            candidateDiffId = null,
            dispatchRef = null,
            promotionReportId = null,
            championId = ledger.activeChampion(competitionSlug)?.championId,
            note = "Cycle opened by owner.",
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
        persist(state)
        return CycleOpenResult.Opened(state)
    }

    /**
     * Steps 1–3 of the loop in one call: ingest → diagnose → propose.
     * Returns [DailyCycleStepResult.AwaitingOwner] when a candidate is ready, and
     * [DailyCycleStepResult.Advanced] when today has nothing to improve.
     */
    fun ingestDiagnoseAndPropose(
        cycleId: String,
        rawDiagnosticsJson: String,
        runLabel: String,
        sourceRef: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DailyCycleStepResult {
        val cycle = load(cycleId) ?: return blockedMissing(cycleId)
        if (cycle.stage.isTerminal && cycle.stage != DailyCycleStage.BLOCKED) {
            return DailyCycleStepResult.Blocked(cycle, "Cycle is already '${cycle.stage.label}'.")
        }

        return when (val ingested = ingest.ingestDiagnosticsJson(rawDiagnosticsJson, cycle.competitionSlug, runLabel, sourceRef, nowEpochMs)) {
            is TelemetryIngestOutcome.Rejected -> {
                val blocked = cycle.copy(
                    stage = DailyCycleStage.BLOCKED,
                    note = "Telemetry rejected (${ingested.reason}): ${ingested.detail}",
                    updatedAtEpochMs = nowEpochMs,
                )
                persist(blocked)
                recordTimeline(blocked, TimelineStage.OBSERVATION, TimelineOutcomeState.BLOCKED, blocked.note, nowEpochMs)
                DailyCycleStepResult.Blocked(blocked, ingested.detail)
            }
            is TelemetryIngestOutcome.Ingested -> {
                val telemetry = ingested.telemetry
                recordTimeline(
                    cycle, TimelineStage.OBSERVATION, TimelineOutcomeState.VERIFIED,
                    "Ingested telemetry: ${telemetry.summaryLine()}", nowEpochMs,
                )
                val champion = ledger.activeChampion(cycle.competitionSlug)
                val report = diagnoser.diagnose(telemetry, champion?.toDiagnosisBaseline(), nowEpochMs)
                persistDiagnosis(report, nowEpochMs)

                val afterDiagnosis = cycle.copy(
                    telemetryId = telemetry.telemetryId,
                    diagnosisReportId = report.reportId,
                    stage = DailyCycleStage.DIAGNOSED,
                    championId = champion?.championId,
                    note = report.headline(),
                    updatedAtEpochMs = nowEpochMs,
                )
                persist(afterDiagnosis)
                recordTimeline(
                    afterDiagnosis, TimelineStage.VERIFICATION, TimelineOutcomeState.VERIFIED,
                    "Diagnosis: ${report.headline()}", nowEpochMs,
                )

                when (val proposal = synthesizer.synthesize(report, telemetry, champion?.toDiagnosisBaseline(), nowEpochMs)) {
                    is DiffSynthesisOutcome.NoActionNeeded -> {
                        val done = afterDiagnosis.copy(
                            stage = DailyCycleStage.NO_ACTION_NEEDED,
                            note = proposal.reason,
                            updatedAtEpochMs = nowEpochMs,
                        )
                        persist(done)
                        recordTimeline(done, TimelineStage.VERIFICATION, TimelineOutcomeState.VERIFIED, proposal.reason, nowEpochMs)
                        DailyCycleStepResult.Advanced(done, proposal.reason)
                    }
                    is DiffSynthesisOutcome.Rejected -> {
                        val blocked = afterDiagnosis.copy(
                            stage = DailyCycleStage.BLOCKED,
                            note = "Candidate rejected (${proposal.reason}): ${proposal.detail}",
                            updatedAtEpochMs = nowEpochMs,
                        )
                        persist(blocked)
                        recordTimeline(blocked, TimelineStage.VERIFICATION, TimelineOutcomeState.BLOCKED, blocked.note, nowEpochMs)
                        DailyCycleStepResult.Blocked(blocked, blocked.note)
                    }
                    is DiffSynthesisOutcome.Proposed -> {
                        persistDiff(proposal.diff, nowEpochMs)
                        val ready = afterDiagnosis.copy(
                            stage = DailyCycleStage.PROPOSED,
                            candidateDiffId = proposal.diff.diffId,
                            note = "Candidate ${proposal.diff.diffId}: ${proposal.diff.patchSummary()}",
                            updatedAtEpochMs = nowEpochMs,
                        )
                        persist(ready)
                        recordTimeline(
                            ready, TimelineStage.PLAN, TimelineOutcomeState.PENDING,
                            "Proposed ${proposal.diff.lever.label}: ${proposal.diff.patchSummary()}", nowEpochMs,
                        )
                        DailyCycleStepResult.AwaitingOwner(
                            ready,
                            "Review candidate ${proposal.diff.diffId} and tap Approve & Run.",
                        )
                    }
                }
            }
        }
    }

    /** Step 4: owner approval → durable intent → transport. */
    fun approveAndDispatch(
        cycleId: String,
        approvedByOwner: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DailyCycleStepResult {
        val cycle = load(cycleId) ?: return blockedMissing(cycleId)
        require(approvedByOwner) { "Dispatching an experiment requires explicit owner approval." }
        // Double-tap and restart safety: re-entering a dispatched cycle never re-sends.
        if (cycle.stage == DailyCycleStage.DISPATCHED) {
            return DailyCycleStepResult.Advanced(cycle, "Already dispatched as ${cycle.dispatchRef}; awaiting telemetry.")
        }
        if (cycle.stage == DailyCycleStage.AWAITING_RECONCILIATION) {
            return DailyCycleStepResult.Blocked(
                cycle,
                "A previous dispatch is uncertain; reconcile it before sending again.",
            )
        }
        if (cycle.stage != DailyCycleStage.PROPOSED) {
            return DailyCycleStepResult.Blocked(cycle, "Cycle is '${cycle.stage.label}', not awaiting dispatch.")
        }
        val diff = loadDiff(cycle.candidateDiffId) ?: return blockCycle(
            cycle, "Candidate diff is missing from local storage.", nowEpochMs,
        )

        // Stop / takeover must bite here, before any external action.
        val lease = leaseController.currentSnapshot()
        if (lease.mode != ControlLeaseMode.AUTOMATION_ACTIVE) {
            val blocked = cycle.copy(
                stage = DailyCycleStage.BLOCKED,
                note = "Control lease is '${lease.mode.label}'; dispatch refused until explicit owner resume.",
                updatedAtEpochMs = nowEpochMs,
            )
            persist(blocked)
            recordTimeline(
                blocked, TimelineStage.TAKEOVER_OR_STOP, TimelineOutcomeState.BLOCKED, blocked.note, nowEpochMs,
            )
            return DailyCycleStepResult.Blocked(blocked, blocked.note)
        }

        val intentKey = "${cycle.cycleId}|${diff.diffId}"
        val priorIntent = existingIntent(intentKey)
        if (priorIntent != null) {
            when (priorIntent.state) {
                DispatchIntentState.DISPATCHED -> return DailyCycleStepResult.Advanced(
                    cycle.copy(
                        stage = DailyCycleStage.DISPATCHED,
                        dispatchRef = priorIntent.dispatchRef,
                        note = "Already dispatched as ${priorIntent.dispatchRef}.",
                    ),
                    "Already dispatched.",
                )
                DispatchIntentState.UNCERTAIN -> return DailyCycleStepResult.Blocked(
                    cycle.copy(stage = DailyCycleStage.AWAITING_RECONCILIATION, note = priorIntent.note),
                    "A previous dispatch is uncertain; reconcile it before sending again.",
                )
                else -> Unit
            }
        }

        // Durable pre-send intent (Division 38 pattern): persisted before the transport call.
        val prepared = DispatchIntent(
            intentKey = intentKey,
            cycleId = cycle.cycleId,
            channel = cycle.channel,
            candidateDiffId = diff.diffId,
            state = DispatchIntentState.PREPARED,
            dispatchRef = null,
            note = "Prepared for dispatch.",
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
        persistIntent(prepared)

        val request = DispatchRequest(
            requestId = "req_${UUID.randomUUID().toString().take(8)}",
            cycleId = cycle.cycleId,
            competitionSlug = cycle.competitionSlug,
            channel = cycle.channel,
            targetRef = diff.baseVersionRef,
            candidateDiffId = diff.diffId,
            diffPatch = diff.patch,
            baseVersionRef = diff.baseVersionRef,
            idempotencyKey = intentKey,
            dryRun = false,
            plannedMaxTokensPerAction = diff.plannedMaxTokensPerAction,
        )

        val outcome = dispatchRouter.dispatch(cycle.channel, request)
        val (intentState, note, dispatchRef) = when (outcome) {
            is DispatchOutcome.Accepted -> Triple(DispatchIntentState.DISPATCHED, outcome.note, outcome.dispatchRef)
            is DispatchOutcome.Rejected -> Triple(DispatchIntentState.REJECTED, outcome.reason, null)
            is DispatchOutcome.UncertainExternalOutcome -> Triple(DispatchIntentState.UNCERTAIN, outcome.note, null)
            is DispatchOutcome.NotConfigured -> Triple(DispatchIntentState.NOT_CONFIGURED, outcome.missing, null)
        }
        persistIntent(
            prepared.copy(
                state = intentState,
                dispatchRef = dispatchRef,
                note = note,
                updatedAtEpochMs = nowEpochMs,
            ),
        )

        val nextStage = when (intentState) {
            DispatchIntentState.DISPATCHED -> DailyCycleStage.DISPATCHED
            DispatchIntentState.UNCERTAIN -> DailyCycleStage.AWAITING_RECONCILIATION
            else -> DailyCycleStage.BLOCKED
        }
        val updated = cycle.copy(
            stage = nextStage,
            dispatchRef = dispatchRef,
            note = note,
            updatedAtEpochMs = nowEpochMs,
        )
        persist(updated)
        recordTimeline(
            updated,
            TimelineStage.ACTION,
            when (intentState) {
                DispatchIntentState.DISPATCHED -> TimelineOutcomeState.VERIFIED
                DispatchIntentState.UNCERTAIN -> TimelineOutcomeState.UNCERTAIN
                else -> TimelineOutcomeState.BLOCKED
            },
            "$note",
            nowEpochMs,
        )
        return if (nextStage == DailyCycleStage.DISPATCHED) {
            DailyCycleStepResult.Advanced(updated, note)
        } else {
            DailyCycleStepResult.Blocked(updated, note)
        }
    }

    /** Reconciles a dispatch whose outcome was unknown, before anything is retried. */
    fun reconcileDispatch(
        cycleId: String,
        landed: Boolean,
        dispatchRef: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DailyCycleStepResult {
        val cycle = load(cycleId) ?: return blockedMissing(cycleId)
        val diff = loadDiff(cycle.candidateDiffId) ?: return blockCycle(cycle, "Candidate diff is missing.", nowEpochMs)
        val intentKey = "${cycle.cycleId}|${diff.diffId}"
        val intent = existingIntent(intentKey) ?: return blockCycle(cycle, "No dispatch intent to reconcile.", nowEpochMs)
        val updatedIntent = intent.copy(
            state = if (landed) DispatchIntentState.DISPATCHED else DispatchIntentState.REJECTED,
            dispatchRef = dispatchRef ?: intent.dispatchRef,
            note = if (landed) "Reconciled: the runner did receive the request." else "Reconciled: the request did not land.",
            updatedAtEpochMs = nowEpochMs,
        )
        persistIntent(updatedIntent)
        val updated = cycle.copy(
            stage = if (landed) DailyCycleStage.DISPATCHED else DailyCycleStage.PROPOSED,
            dispatchRef = updatedIntent.dispatchRef,
            note = updatedIntent.note,
            updatedAtEpochMs = nowEpochMs,
        )
        persist(updated)
        recordTimeline(updated, TimelineStage.VERIFICATION, TimelineOutcomeState.VERIFIED, updated.note, nowEpochMs)
        return DailyCycleStepResult.Advanced(updated, updated.note)
    }

    /** Step 5–6: the candidate run's telemetry arrives, and the fixed gate decides. */
    fun recordCandidateTelemetry(
        cycleId: String,
        rawDiagnosticsJson: String,
        runLabel: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DailyCycleStepResult {
        val cycle = load(cycleId) ?: return blockedMissing(cycleId)
        if (cycle.stage !in setOf(DailyCycleStage.DISPATCHED, DailyCycleStage.AWAITING_OWNER_APPROVAL)) {
            return DailyCycleStepResult.Blocked(
                cycle,
                "Cycle is '${cycle.stage.label}'; candidate telemetry is only accepted after dispatch.",
            )
        }
        return when (val ingested = ingest.ingestDiagnosticsJson(
            rawDiagnosticsJson,
            cycle.competitionSlug,
            runLabel,
            cycle.dispatchRef,
            nowEpochMs,
        )) {
            is TelemetryIngestOutcome.Rejected -> blockCycle(
                cycle, "Candidate telemetry rejected (${ingested.reason}): ${ingested.detail}", nowEpochMs,
            )
            is TelemetryIngestOutcome.Ingested -> {
                val telemetry = ingested.telemetry
                recordTimeline(
                    cycle, TimelineStage.OBSERVATION, TimelineOutcomeState.VERIFIED,
                    "Candidate telemetry: ${telemetry.summaryLine()}", nowEpochMs,
                )
                val outcome = gate.evaluate(telemetry, approvedByOwner = false, nowEpochMs = nowEpochMs)
                val (stage, note) = when (outcome) {
                    is PromotionGateOutcome.AwaitingOwnerApproval ->
                        DailyCycleStage.GATED to outcome.report.headline()
                    is PromotionGateOutcome.Rejected ->
                        DailyCycleStage.REJECTED to outcome.report.headline()
                    is PromotionGateOutcome.Inconclusive ->
                        DailyCycleStage.INCONCLUSIVE to outcome.report.headline()
                    is PromotionGateOutcome.Blocked ->
                        DailyCycleStage.BLOCKED to outcome.report.headline()
                    is PromotionGateOutcome.Promoted ->
                        DailyCycleStage.PROMOTED to outcome.report.headline()
                }
                val updated = cycle.copy(
                    stage = stage,
                    telemetryId = telemetry.telemetryId,
                    promotionReportId = outcome.report.reportId,
                    note = note,
                    updatedAtEpochMs = nowEpochMs,
                )
                persist(updated)
                recordTimeline(
                    updated, TimelineStage.VERIFICATION,
                    if (stage == DailyCycleStage.GATED) TimelineOutcomeState.PENDING else TimelineOutcomeState.VERIFIED,
                    "Gate: $note", nowEpochMs,
                )
                if (stage == DailyCycleStage.GATED) {
                    DailyCycleStepResult.AwaitingOwner(updated, "Approve or decline promotion for ${outcome.report.reportId}.")
                } else {
                    DailyCycleStepResult.Advanced(updated, note)
                }
            }
        }
    }

    /** The owner's 1-tap promotion decision. The only route to a new champion. */
    fun resolvePromotion(
        cycleId: String,
        approvedByOwner: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DailyCycleStepResult {
        val cycle = load(cycleId) ?: return blockedMissing(cycleId)
        if (cycle.stage != DailyCycleStage.GATED) {
            return DailyCycleStepResult.Blocked(cycle, "Cycle is '${cycle.stage.label}', not awaiting a promotion decision.")
        }
        val reportId = cycle.promotionReportId
            ?: return blockCycle(cycle, "Cycle has no promotion report to decide on.", nowEpochMs)

        val outcome = gate.applyOwnerDecision(
            reportId = reportId,
            approvedByOwner = approvedByOwner,
            telemetryLookup = { id -> ingest.get(id) },
            nowEpochMs = nowEpochMs,
        )
        val (stage, note, championId) = when (outcome) {
            is PromotionGateOutcome.Promoted -> Triple(
                DailyCycleStage.PROMOTED,
                "Promoted ${outcome.report.candidateVersionRef}; new champion ${outcome.champion.championId}.",
                outcome.champion.championId,
            )
            is PromotionGateOutcome.Rejected -> Triple(
                DailyCycleStage.REJECTED,
                outcome.report.notes.lastOrNull() ?: "Rejected — champion retained.",
                cycle.championId,
            )
            is PromotionGateOutcome.Inconclusive -> Triple(
                DailyCycleStage.INCONCLUSIVE,
                outcome.report.notes.lastOrNull() ?: "Inconclusive — champion retained.",
                cycle.championId,
            )
            is PromotionGateOutcome.Blocked -> Triple(
                DailyCycleStage.BLOCKED,
                outcome.report.notes.lastOrNull() ?: "Blocked.",
                cycle.championId,
            )
            is PromotionGateOutcome.AwaitingOwnerApproval -> Triple(
                DailyCycleStage.GATED,
                outcome.report.headline(),
                cycle.championId,
            )
        }
        val updated = cycle.copy(
            stage = stage,
            championId = championId,
            note = note,
            updatedAtEpochMs = nowEpochMs,
        )
        persist(updated)
        recordTimeline(
            updated,
            TimelineStage.APPROVAL_CHECK,
            if (stage == DailyCycleStage.PROMOTED) TimelineOutcomeState.VERIFIED else TimelineOutcomeState.BLOCKED,
            note,
            nowEpochMs,
        )
        return if (stage == DailyCycleStage.PROMOTED) {
            DailyCycleStepResult.Advanced(updated, note)
        } else {
            DailyCycleStepResult.Blocked(updated, note)
        }
    }

    /** Owner Stop. Terminal, immediate, and recorded on the action timeline. */
    fun stopCycle(cycleId: String, reason: String = "Stopped by owner.", nowEpochMs: Long = System.currentTimeMillis()): DailyCycleState? {
        val cycle = load(cycleId) ?: return null
        val stopped = cycle.copy(stage = DailyCycleStage.STOPPED, note = reason, updatedAtEpochMs = nowEpochMs)
        persist(stopped)
        recordTimeline(stopped, TimelineStage.TAKEOVER_OR_STOP, TimelineOutcomeState.BLOCKED, reason, nowEpochMs)
        return stopped
    }

    /** Locks the first champion from a stored run — the bootstrap step before any cycle is useful. */
    fun lockStartingChampion(
        telemetryId: String,
        ownerConfirmed: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ChampionRecord? {
        val telemetry = ingest.get(telemetryId) ?: return null
        val champion = ledger.lockInitialChampion(telemetry, ownerConfirmed, nowEpochMs)
        champion?.let {
            recordTimelineForSlug(
                it.competitionSlug, TimelineStage.APPROVAL_CHECK, TimelineOutcomeState.VERIFIED,
                "Locked starting champion ${it.versionRef} (${it.metricName}=${formatMetric(it.score)}).", nowEpochMs,
            )
        }
        return champion
    }

    // ---------------------------------------------------------------- queries ----

    fun load(cycleId: String): DailyCycleState? {
        val row = database.backend.get(NS_CYCLES, cycleId) ?: return null
        return runCatching { DailyCycleState.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun activeCycle(competitionSlug: String): DailyCycleState? =
        listCycles(competitionSlug, limit = Int.MAX_VALUE).firstOrNull { !it.stage.isTerminal }

    fun listCycles(competitionSlug: String, limit: Int = 20): List<DailyCycleState> =
        database.backend.listByNamespace(NS_CYCLES)
            .mapNotNull { row ->
                runCatching { DailyCycleState.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }
            .filter { it.competitionSlug == competitionSlug }
            .sortedByDescending { it.updatedAtEpochMs }
            .take(limit)

    fun loadDiff(diffId: String?): BoundedCandidateDiff? {
        if (diffId == null) return null
        val row = database.backend.get(NS_DIFFS, diffId) ?: return null
        return runCatching { BoundedCandidateDiff.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun loadDiagnosis(reportId: String?): DiagnosisReport? {
        if (reportId == null) return null
        val row = database.backend.get(NS_DIAGNOSES, reportId) ?: return null
        return runCatching { DiagnosisReport.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun existingIntent(intentKey: String): DispatchIntent? {
        val row = database.backend.get(NS_DISPATCH_INTENTS, intentKey) ?: return null
        return runCatching { DispatchIntent.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    /** Honest status for the Daily Lab header: what is ready to dispatch right now, per channel. */
    fun dispatchReadiness(): Map<DispatchChannel, Boolean> = dispatchRouter.readiness()

    // ---------------------------------------------------------------- internals ----

    private fun persist(state: DailyCycleState) {
        database.backend.upsert(
            namespace = NS_CYCLES,
            id = state.cycleId,
            payloadJson = JsonCodec.stringify(state.toJson()),
            updatedAtEpochMs = state.updatedAtEpochMs,
        )
    }

    private fun persistDiff(diff: BoundedCandidateDiff, nowEpochMs: Long) {
        database.backend.upsert(
            namespace = NS_DIFFS,
            id = diff.diffId,
            payloadJson = JsonCodec.stringify(diff.toJson()),
            updatedAtEpochMs = nowEpochMs,
        )
    }

    private fun persistDiagnosis(report: DiagnosisReport, nowEpochMs: Long) {
        database.backend.upsert(
            namespace = NS_DIAGNOSES,
            id = report.reportId,
            payloadJson = JsonCodec.stringify(report.toJson()),
            updatedAtEpochMs = nowEpochMs,
        )
    }

    private fun persistIntent(intent: DispatchIntent) {
        database.backend.upsert(
            namespace = NS_DISPATCH_INTENTS,
            id = intent.intentKey,
            payloadJson = JsonCodec.stringify(intent.toJson()),
            updatedAtEpochMs = intent.updatedAtEpochMs,
        )
    }

    private fun blockCycle(cycle: DailyCycleState, reason: String, nowEpochMs: Long): DailyCycleStepResult {
        val blocked = cycle.copy(stage = DailyCycleStage.BLOCKED, note = reason, updatedAtEpochMs = nowEpochMs)
        persist(blocked)
        recordTimeline(blocked, TimelineStage.VERIFICATION, TimelineOutcomeState.BLOCKED, reason, nowEpochMs)
        return DailyCycleStepResult.Blocked(blocked, reason)
    }

    private fun blockedMissing(cycleId: String): DailyCycleStepResult.Blocked {
        val placeholder = DailyCycleState(
            cycleId = cycleId,
            competitionSlug = "",
            benchmarkKind = BenchmarkKind.ARC_AGI_INTERACTIVE,
            channel = DispatchChannel.COLAB_MANUAL,
            stage = DailyCycleStage.BLOCKED,
            telemetryId = null,
            diagnosisReportId = null,
            candidateDiffId = null,
            dispatchRef = null,
            promotionReportId = null,
            championId = null,
            note = "Cycle '$cycleId' was not found.",
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )
        return DailyCycleStepResult.Blocked(placeholder, placeholder.note)
    }

    private fun recordTimeline(
        cycle: DailyCycleState,
        stage: TimelineStage,
        outcome: TimelineOutcomeState,
        summary: String,
        nowEpochMs: Long,
    ) {
        timeline.recordEvent(
            stage = stage,
            actor = TimelineActor.LOCAL_BROKER,
            summary = summary,
            outcome = outcome,
            jobId = cycle.cycleId,
            targetReference = cycle.competitionSlug,
            nowEpochMs = nowEpochMs,
        )
    }

    private fun recordTimelineForSlug(
        competitionSlug: String,
        stage: TimelineStage,
        outcome: TimelineOutcomeState,
        summary: String,
        nowEpochMs: Long,
    ) {
        timeline.recordEvent(
            stage = stage,
            actor = TimelineActor.LOCAL_BROKER,
            summary = summary,
            outcome = outcome,
            jobId = null,
            targetReference = competitionSlug,
            nowEpochMs = nowEpochMs,
        )
    }

    companion object {
        const val NS_CYCLES = "daily_cycles"
        const val NS_DIFFS = "candidate_diffs"
        const val NS_DIAGNOSES = "diagnosis_reports"
        const val NS_DISPATCH_INTENTS = "daily_dispatch_intents"

        /** Wires the whole loop with owner-approved caps and the device's installed transports. */
        fun wire(
            database: FourCBOn2Database,
            dispatchRouter: ExperimentDispatchRouter,
            caps: ExperimentBudgetCaps = ExperimentBudgetCaps.OWNER_APPROVED_DEFAULT,
            thresholds: DiagnosisThresholds = DiagnosisThresholds.DEFAULT,
        ): DailyExperimentCycle {
            val ledger = ChampionLedger(database)
            return DailyExperimentCycle(
                database = database,
                ingest = RunTelemetryIngest(database),
                ledger = ledger,
                gate = FixedPromotionGate(ledger = ledger, database = database),
                dispatchRouter = dispatchRouter,
                leaseController = TakeoverLeaseController(database),
                timeline = ActionTimelineRepository(database),
                diagnoser = RunDiagnoser(thresholds),
                synthesizer = CandidateDiffSynthesizer(BoundedPatchPolicy(caps)),
                thresholds = thresholds,
            )
        }
    }
}
