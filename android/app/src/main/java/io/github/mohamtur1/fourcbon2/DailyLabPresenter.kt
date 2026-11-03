package io.github.mohamtur1.fourcbon2

/**
 * Division 50 (v0.3) — Daily Lab presentation model.
 *
 * The Compose surface is deliberately dumb: it renders strings and wires three taps
 * (`Approve & Run`, `Approve Promotion`, `Roll back`). All judgement — what is safe to show, what the
 * owner can do next, whether an alert is warranted — lives here in pure Kotlin so it is unit-tested
 * without an emulator.
 */
data class DailyLabCard(
    val competitionSlug: String,
    val headline: String,
    val stageLabel: String,
    val nextAction: String,
    val championLine: String,
    val latestRunLine: String,
    val candidateLine: String,
    val gateLine: String,
    val dispatchLine: String,
    val alert: Boolean,
    val canLockChampion: Boolean,
    val canApproveDispatch: Boolean,
    val canApprovePromotion: Boolean,
    val canRollback: Boolean,
    val recentTimeline: List<String>,
) {
    /** Plain-text rendering used by the timeline/export path and by tests. */
    fun toDisplayText(): String = buildString {
        appendLine(headline)
        appendLine("Stage: $stageLabel")
        appendLine("Next: $nextAction")
        appendLine(championLine)
        appendLine(latestRunLine)
        if (candidateLine.isNotBlank()) appendLine(candidateLine)
        if (gateLine.isNotBlank()) appendLine(gateLine)
        if (dispatchLine.isNotBlank()) appendLine(dispatchLine)
        recentTimeline.forEach { appendLine("• $it") }
    }
}

class DailyLabPresenter(
    private val cycle: DailyExperimentCycle,
    private val ingest: RunTelemetryIngest,
    private val ledger: ChampionLedger,
    private val gate: FixedPromotionGate,
    private val timeline: ActionTimelineRepository,
) {
    fun card(competitionSlug: String, timelineLimit: Int = 5): DailyLabCard {
        val champion = ledger.activeChampion(competitionSlug)
        val state = cycle.activeCycle(competitionSlug) ?: cycle.listCycles(competitionSlug, limit = 1).firstOrNull()
        val latestRun = ingest.latestFor(competitionSlug)
        val diff = cycle.loadDiff(state?.candidateDiffId)
        val diagnosis = cycle.loadDiagnosis(state?.diagnosisReportId)
        val report = state?.promotionReportId?.let { gate.getReport(it) }

        val headline = when {
            state == null && champion == null -> "No runs yet — paste a diagnostics payload to start the lab."
            state == null -> "Champion ${champion!!.versionRef} is locked. Start a cycle to improve on it."
            diagnosis != null -> diagnosis.headline()
            else -> state.stage.label
        }

        val championLine = champion?.let {
            "Champion ${it.versionRef}: ${it.metricName}=${formatMetric(it.score)} · " +
                "${it.levelsCompleted} levels · ${formatMetric(it.tokensPerAction ?: 0.0)} tokens/action"
        } ?: "No champion locked yet."

        val latestRunLine = latestRun?.let { "Latest run: ${it.summaryLine()}" } ?: "No ingested telemetry."

        val candidateLine = diff?.let {
            "Candidate ${it.diffId} (${it.lever.label}) · ${it.patchSummary()} · risk ${it.risk.label}"
        } ?: ""

        val gateLine = report?.let { "Gate: ${it.verdict.label} — ${it.headline()}" } ?: ""

        val dispatchLine = state?.let { s ->
            val intent = s.candidateDiffId?.let { cycle.existingIntent("${s.cycleId}|$it") }
            when {
                intent == null -> ""
                intent.state == DispatchIntentState.UNCERTAIN ->
                    "Dispatch uncertain: ${intent.note} — reconcile before retrying."
                else -> "Dispatch: ${intent.state.label}${intent.dispatchRef?.let { ref -> " ($ref)" } ?: ""}"
            }
        } ?: ""

        val readiness = cycle.dispatchReadiness()

        return DailyLabCard(
            competitionSlug = competitionSlug,
            headline = headline,
            stageLabel = state?.stage?.label ?: "Idle",
            nextAction = state?.nextOwnerAction() ?: "Paste the latest run's diagnostics JSON to ingest it.",
            championLine = championLine,
            latestRunLine = latestRunLine,
            candidateLine = candidateLine,
            gateLine = gateLine,
            dispatchLine = dispatchLine,
            alert = diagnosis?.requiresOwnerVisibleAlert == true,
            canLockChampion = champion == null && latestRun != null,
            canApproveDispatch = state?.stage == DailyCycleStage.PROPOSED &&
                state.candidateDiffId != null &&
                readiness[state.channel] == true,
            canApprovePromotion = state?.stage == DailyCycleStage.GATED,
            canRollback = champion?.promotionReportId != null,
            recentTimeline = timeline.listEvents(limit = timelineLimit)
                .filter { it.summary.isNotBlank() }
                .map { "${it.stage.label}: ${it.summary}" },
        )
    }

    /** Compact history for the "previous cycles" list. */
    fun cycleHistory(competitionSlug: String, limit: Int = 10): List<String> =
        cycle.listCycles(competitionSlug, limit = limit).map { state ->
            "${state.stage.label} · ${state.note}"
        }
}
