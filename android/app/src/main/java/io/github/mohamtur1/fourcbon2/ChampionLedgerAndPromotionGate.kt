package io.github.mohamtur1.fourcbon2

import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * Division 47 (v0.3) — Champion ledger and fixed promotion gate.
 *
 * Division 39 could only promote inside a single lab session. The daily loop needs a *durable,
 * locked champion* per competition: an immutable record of what "current best" actually means, with
 * full history and one-step rollback. The gate compares a fresh run against that champion and is the
 * only code path allowed to move the pointer.
 *
 * Non-negotiables mirrored from `AgentLab_Blueprint.md` §10:
 *  - A candidate can never promote itself: [PromotionGateOutcome.Promoted] is only reachable with
 *    `approvedByOwner = true`.
 *  - Inconclusive data keeps the baseline. "No observed failure" is not proof of improvement.
 *  - Every promotion and rollback is appended to history; nothing is overwritten in place.
 */

enum class ChampionState(val label: String) {
    ACTIVE("Active champion"),
    SUPERSEDED("Superseded"),
    ROLLED_BACK("Rolled back"),
}

data class ChampionRecord(
    val championId: String,
    val competitionSlug: String,
    val benchmarkKind: BenchmarkKind,
    val versionRef: String,
    val metricName: String,
    val higherIsBetter: Boolean,
    val score: Double,
    val levelsCompleted: Int,
    val totalActions: Int,
    val tokensUsed: Long,
    val tokensPerAction: Double?,
    val wallClockMs: Long,
    val sourceTelemetryId: String,
    val promotionReportId: String?,
    val state: ChampionState,
    val promotedAtEpochMs: Long,
) {
    fun toDiagnosisBaseline(): DiagnosisBaseline = DiagnosisBaseline(
        versionRef = versionRef,
        metricName = metricName,
        primaryMetric = score,
        higherIsBetter = higherIsBetter,
        tokensPerAction = tokensPerAction,
        actionsPerLevel = if (levelsCompleted > 0) totalActions.toDouble() / levelsCompleted else null,
        levelsCompleted = levelsCompleted,
        totalActions = totalActions,
        wallClockMs = wallClockMs,
    )

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "championId" to JsonValue.Str(championId),
            "competitionSlug" to JsonValue.Str(competitionSlug),
            "benchmarkKind" to JsonValue.Str(benchmarkKind.name),
            "versionRef" to JsonValue.Str(versionRef),
            "metricName" to JsonValue.Str(metricName),
            "higherIsBetter" to JsonValue.Bool(higherIsBetter),
            "score" to JsonValue.Num(score.coerceFinite()),
            "levelsCompleted" to JsonValue.Num(levelsCompleted.toDouble(), levelsCompleted.toString()),
            "totalActions" to JsonValue.Num(totalActions.toDouble(), totalActions.toString()),
            "tokensUsed" to JsonValue.Num(tokensUsed.toDouble(), tokensUsed.toString()),
            "tokensPerAction" to (tokensPerAction?.let { JsonValue.Num(it) } ?: JsonValue.Null),
            "wallClockMs" to JsonValue.Num(wallClockMs.toDouble(), wallClockMs.toString()),
            "sourceTelemetryId" to JsonValue.Str(sourceTelemetryId),
            "promotionReportId" to (promotionReportId?.let(JsonValue::Str) ?: JsonValue.Null),
            "state" to JsonValue.Str(state.name),
            "promotedAtEpochMs" to JsonValue.Num(promotedAtEpochMs.toDouble(), promotedAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): ChampionRecord = ChampionRecord(
            championId = obj.stringOrDefault("championId"),
            competitionSlug = obj.stringOrDefault("competitionSlug"),
            benchmarkKind = runCatching { BenchmarkKind.valueOf(obj.stringOrDefault("benchmarkKind")) }
                .getOrDefault(BenchmarkKind.SCALAR_METRIC),
            versionRef = obj.stringOrDefault("versionRef"),
            metricName = obj.stringOrDefault("metricName"),
            higherIsBetter = obj.bool("higherIsBetter", true),
            score = obj.double("score", 0.0),
            levelsCompleted = obj.int("levelsCompleted", 0),
            totalActions = obj.int("totalActions", 0),
            tokensUsed = obj.long("tokensUsed", 0L),
            tokensPerAction = (obj.entries["tokensPerAction"] as? JsonValue.Num)?.value,
            wallClockMs = obj.long("wallClockMs", 0L),
            sourceTelemetryId = obj.stringOrDefault("sourceTelemetryId"),
            promotionReportId = obj.string("promotionReportId"),
            state = runCatching { ChampionState.valueOf(obj.stringOrDefault("state")) }
                .getOrDefault(ChampionState.ACTIVE),
            promotedAtEpochMs = obj.long("promotedAtEpochMs", 0L),
        )
    }
}

/** Outcome of comparing one run with the locked champion. */
enum class PromotionVerdict(val label: String) {
    AWAITING_OWNER_APPROVAL("Eligible — awaiting owner approval"),
    PROMOTED("Promoted to champion"),
    REJECTED("Rejected — champion retained"),
    INCONCLUSIVE("Inconclusive — champion retained"),
    BLOCKED("Blocked — evidence unusable"),
}

data class PromotionReport(
    val reportId: String,
    val competitionSlug: String,
    val candidateTelemetryId: String,
    val candidateVersionRef: String,
    val championVersionRef: String?,
    val benchmarkKind: BenchmarkKind,
    val evaluatorId: String?,
    val metricName: String,
    val higherIsBetter: Boolean,
    val candidateScore: Double?,
    val championScore: Double?,
    val qualityDelta: Double?,
    val relativeQualityDelta: Double?,
    val candidateTokensPerAction: Double?,
    val championTokensPerAction: Double?,
    val tokenPaceDeltaPercent: Double?,
    val minimumQualityGainRelative: Double,
    val costEnvelopeTolerance: Double,
    val passesHardGates: Boolean,
    val notes: List<String>,
    val verdict: PromotionVerdict,
    val createdAtEpochMs: Long,
    val decidesAtEpochMs: Long? = null,
    val contractVersion: String = CONTRACT_VERSION,
) {
    fun headline(): String = when (verdict) {
        PromotionVerdict.PROMOTED -> "Promoted $candidateVersionRef over ${championVersionRef ?: "—"}."
        PromotionVerdict.AWAITING_OWNER_APPROVAL ->
            "Eligible: ${formatMetric(candidateScore ?: Double.NaN)} vs champion " +
                "${formatMetric(championScore ?: Double.NaN)} — owner approval required."
        PromotionVerdict.REJECTED, PromotionVerdict.INCONCLUSIVE, PromotionVerdict.BLOCKED ->
            notes.firstOrNull() ?: verdict.label
    }

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "contractVersion" to JsonValue.Str(contractVersion),
            "reportId" to JsonValue.Str(reportId),
            "competitionSlug" to JsonValue.Str(competitionSlug),
            "candidateTelemetryId" to JsonValue.Str(candidateTelemetryId),
            "candidateVersionRef" to JsonValue.Str(candidateVersionRef),
            "championVersionRef" to (championVersionRef?.let(JsonValue::Str) ?: JsonValue.Null),
            "benchmarkKind" to JsonValue.Str(benchmarkKind.name),
            "evaluatorId" to (evaluatorId?.let(JsonValue::Str) ?: JsonValue.Null),
            "metricName" to JsonValue.Str(metricName),
            "higherIsBetter" to JsonValue.Bool(higherIsBetter),
            "candidateScore" to (candidateScore?.let { JsonValue.Num(it) } ?: JsonValue.Null),
            "championScore" to (championScore?.let { JsonValue.Num(it) } ?: JsonValue.Null),
            "qualityDelta" to (qualityDelta?.let { JsonValue.Num(it) } ?: JsonValue.Null),
            "relativeQualityDelta" to (relativeQualityDelta?.let { JsonValue.Num(it) } ?: JsonValue.Null),
            "candidateTokensPerAction" to (candidateTokensPerAction?.let { JsonValue.Num(it) } ?: JsonValue.Null),
            "championTokensPerAction" to (championTokensPerAction?.let { JsonValue.Num(it) } ?: JsonValue.Null),
            "tokenPaceDeltaPercent" to (tokenPaceDeltaPercent?.let { JsonValue.Num(it) } ?: JsonValue.Null),
            "minimumQualityGainRelative" to JsonValue.Num(minimumQualityGainRelative),
            "costEnvelopeTolerance" to JsonValue.Num(costEnvelopeTolerance),
            "passesHardGates" to JsonValue.Bool(passesHardGates),
            "notes" to JsonValue.Arr(notes.map { JsonValue.Str(it) }),
            "verdict" to JsonValue.Str(verdict.name),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
            "decidesAtEpochMs" to (decidesAtEpochMs?.let { JsonValue.Num(it.toDouble(), it.toString()) } ?: JsonValue.Null),
        ),
    )

    companion object {
        const val CONTRACT_VERSION = "4cbon2.promotion_report.v1"

        fun fromJson(obj: JsonValue.Obj): PromotionReport = PromotionReport(
            reportId = obj.stringOrDefault("reportId"),
            competitionSlug = obj.stringOrDefault("competitionSlug"),
            candidateTelemetryId = obj.stringOrDefault("candidateTelemetryId"),
            candidateVersionRef = obj.stringOrDefault("candidateVersionRef"),
            championVersionRef = obj.string("championVersionRef"),
            benchmarkKind = runCatching { BenchmarkKind.valueOf(obj.stringOrDefault("benchmarkKind")) }
                .getOrDefault(BenchmarkKind.SCALAR_METRIC),
            evaluatorId = obj.string("evaluatorId"),
            metricName = obj.stringOrDefault("metricName"),
            higherIsBetter = obj.bool("higherIsBetter", true),
            candidateScore = (obj.entries["candidateScore"] as? JsonValue.Num)?.value,
            championScore = (obj.entries["championScore"] as? JsonValue.Num)?.value,
            qualityDelta = (obj.entries["qualityDelta"] as? JsonValue.Num)?.value,
            relativeQualityDelta = (obj.entries["relativeQualityDelta"] as? JsonValue.Num)?.value,
            candidateTokensPerAction = (obj.entries["candidateTokensPerAction"] as? JsonValue.Num)?.value,
            championTokensPerAction = (obj.entries["championTokensPerAction"] as? JsonValue.Num)?.value,
            tokenPaceDeltaPercent = (obj.entries["tokenPaceDeltaPercent"] as? JsonValue.Num)?.value,
            minimumQualityGainRelative = obj.double("minimumQualityGainRelative", 0.0),
            costEnvelopeTolerance = obj.double("costEnvelopeTolerance", 0.25),
            passesHardGates = obj.bool("passesHardGates", false),
            notes = obj.stringList("notes"),
            verdict = runCatching { PromotionVerdict.valueOf(obj.stringOrDefault("verdict")) }
                .getOrDefault(PromotionVerdict.BLOCKED),
            createdAtEpochMs = obj.long("createdAtEpochMs", 0L),
            decidesAtEpochMs = (obj.entries["decidesAtEpochMs"] as? JsonValue.Num)?.let {
                it.toLongOrNull()
            },
            contractVersion = obj.stringOrDefault("contractVersion", CONTRACT_VERSION),
        )
    }
}

sealed interface PromotionGateOutcome {
    val report: PromotionReport

    data class AwaitingOwnerApproval(override val report: PromotionReport) : PromotionGateOutcome

    data class Promoted(override val report: PromotionReport, val champion: ChampionRecord) : PromotionGateOutcome

    data class Rejected(override val report: PromotionReport) : PromotionGateOutcome

    data class Inconclusive(override val report: PromotionReport) : PromotionGateOutcome

    data class Blocked(override val report: PromotionReport) : PromotionGateOutcome
}

/** Durable, append-only champion history with a single active pointer per competition. */
class ChampionLedger(
    private val database: FourCBOn2Database,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun activeChampion(competitionSlug: String): ChampionRecord? {
        val activeId = database.getSetting(activeKey(competitionSlug)) ?: return null
        return get(activeId)?.takeIf { it.state == ChampionState.ACTIVE }
    }

    fun get(championId: String): ChampionRecord? {
        val row = database.backend.get(NS_CHAMPIONS, championId) ?: return null
        return runCatching { ChampionRecord.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun history(competitionSlug: String): List<ChampionRecord> =
        database.backend.listByNamespace(NS_CHAMPIONS)
            .mapNotNull { row ->
                runCatching { ChampionRecord.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }
            .filter { it.competitionSlug == competitionSlug }
            .sortedByDescending { it.promotedAtEpochMs }

    /**
     * Locks the very first champion. This is an owner action by definition — the caller must have
     * confirmed it in the UI — and it can only happen when no champion exists yet.
     */
    fun lockInitialChampion(
        telemetry: RunTelemetry,
        ownerConfirmed: Boolean,
        nowEpochMs: Long = now(),
    ): ChampionRecord? {
        require(ownerConfirmed) { "Locking a champion requires explicit owner confirmation." }
        if (activeChampion(telemetry.competitionSlug) != null) return null
        val record = ChampionRecord(
            championId = "champ_${UUID.randomUUID().toString().take(8)}",
            competitionSlug = telemetry.competitionSlug,
            benchmarkKind = telemetry.benchmarkKind,
            versionRef = telemetry.versionRef,
            metricName = telemetry.metricName,
            higherIsBetter = telemetry.higherIsBetter,
            score = telemetry.primaryMetric,
            levelsCompleted = telemetry.levelsCompleted,
            totalActions = telemetry.totalActions,
            tokensUsed = telemetry.tokensUsed,
            tokensPerAction = telemetry.tokensPerAction,
            wallClockMs = telemetry.wallClockMs,
            sourceTelemetryId = telemetry.telemetryId,
            promotionReportId = null,
            state = ChampionState.ACTIVE,
            promotedAtEpochMs = nowEpochMs,
        )
        persist(record, nowEpochMs)
        database.putSetting(activeKey(telemetry.competitionSlug), record.championId, nowEpochMs)
        return record
    }

    /** Moves the active pointer. Only [FixedPromotionGate] and owner-driven rollback may call this. */
    internal fun promote(
        telemetry: RunTelemetry,
        reportId: String,
        priorChampion: ChampionRecord?,
        nowEpochMs: Long = now(),
    ): ChampionRecord {
        priorChampion?.let { persist(it.copy(state = ChampionState.SUPERSEDED), nowEpochMs) }
        val record = ChampionRecord(
            championId = "champ_${UUID.randomUUID().toString().take(8)}",
            competitionSlug = telemetry.competitionSlug,
            benchmarkKind = telemetry.benchmarkKind,
            versionRef = telemetry.versionRef,
            metricName = telemetry.metricName,
            higherIsBetter = telemetry.higherIsBetter,
            score = telemetry.primaryMetric,
            levelsCompleted = telemetry.levelsCompleted,
            totalActions = telemetry.totalActions,
            tokensUsed = telemetry.tokensUsed,
            tokensPerAction = telemetry.tokensPerAction,
            wallClockMs = telemetry.wallClockMs,
            sourceTelemetryId = telemetry.telemetryId,
            promotionReportId = reportId,
            state = ChampionState.ACTIVE,
            promotedAtEpochMs = nowEpochMs,
        )
        persist(record, nowEpochMs)
        database.putSetting(activeKey(telemetry.competitionSlug), record.championId, nowEpochMs)
        if (priorChampion != null) {
            database.putSetting(rollbackKey(telemetry.competitionSlug), priorChampion.championId, nowEpochMs)
        }
        return record
    }

    /** One-step rollback to the champion that was active before the last promotion. */
    fun rollbackLastPromotion(
        competitionSlug: String,
        ownerConfirmed: Boolean,
        nowEpochMs: Long = now(),
    ): ChampionRecord? {
        require(ownerConfirmed) { "Rollback requires explicit owner confirmation." }
        val previousId = database.getSetting(rollbackKey(competitionSlug)) ?: return null
        val previous = get(previousId) ?: return null
        activeChampion(competitionSlug)?.let { current ->
            persist(current.copy(state = ChampionState.ROLLED_BACK), nowEpochMs)
        }
        val restored = previous.copy(state = ChampionState.ACTIVE, promotedAtEpochMs = nowEpochMs)
        persist(restored, nowEpochMs)
        database.putSetting(activeKey(competitionSlug), restored.championId, nowEpochMs)
        database.putSetting(rollbackKey(competitionSlug), "", nowEpochMs)
        return restored
    }

    private fun persist(record: ChampionRecord, nowEpochMs: Long) {
        database.backend.upsert(
            namespace = NS_CHAMPIONS,
            id = record.championId,
            payloadJson = JsonCodec.stringify(record.toJson()),
            updatedAtEpochMs = nowEpochMs,
        )
    }

    private fun activeKey(slug: String) = "champion_active_$slug"

    private fun rollbackKey(slug: String) = "champion_rollback_$slug"

    companion object {
        const val NS_CHAMPIONS = "champion_ledger"
    }
}

/**
 * The fixed promotion gate. Everything the gate decides is derived from the fixed evaluator and the
 * locked champion — never from the candidate's own claims.
 */
class FixedPromotionGate(
    private val ledger: ChampionLedger,
    private val database: FourCBOn2Database,
    private val registry: FixedEvaluatorRegistry = FixedEvaluatorRegistry(),
    /** A quality gain below this relative size is treated as noise (inconclusive). */
    private val minimumQualityGainRelative: Double = 0.0,
    /** Cost is allowed to rise by at most this fraction while quality improves. */
    private val costEnvelopeTolerance: Double = 0.25,
    private val reportIdFactory: () -> String = { "promo_${UUID.randomUUID().toString().take(8)}" },
) {
    fun evaluate(
        telemetry: RunTelemetry,
        approvedByOwner: Boolean = false,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): PromotionGateOutcome {
        val champion = ledger.activeChampion(telemetry.competitionSlug)
        val evaluation = registry.evaluate(telemetry)

        if (evaluation !is EvaluationOutcome.Computed) {
            val notes = when (evaluation) {
                is EvaluationOutcome.Inconclusive -> listOf(
                    "Inconclusive (${evaluation.reason}): ${evaluation.detail}",
                )
                is EvaluationOutcome.Rejected -> listOf("Rejected (${evaluation.reason}): ${evaluation.detail}")
                else -> listOf("Evaluator produced no score.")
            }
            val verdict = if (evaluation is EvaluationOutcome.Inconclusive) {
                PromotionVerdict.INCONCLUSIVE
            } else {
                PromotionVerdict.BLOCKED
            }
            val report = baseReport(telemetry, champion, null, null, notes, verdict, nowEpochMs)
            return if (verdict == PromotionVerdict.INCONCLUSIVE) {
                PromotionGateOutcome.Inconclusive(report)
            } else {
                PromotionGateOutcome.Blocked(report)
            }
        }

        val score = evaluation.score
        if (champion == null) {
            val report = baseReport(
                telemetry = telemetry,
                champion = null,
                score = score,
                championScore = null,
                notes = listOf(
                    "No champion is locked for ${telemetry.competitionSlug}; " +
                        "lock this run as the starting champion before promoting.",
                ),
                verdict = PromotionVerdict.BLOCKED,
                nowEpochMs = nowEpochMs,
            )
            return PromotionGateOutcome.Blocked(report)
        }

        val notes = mutableListOf<String>()

        // --- hard gate: comparable evidence ---------------------------------------------------
        if (champion.benchmarkKind != telemetry.benchmarkKind) {
            notes += "Incomparable benchmark: champion is ${champion.benchmarkKind.name}, run is ${telemetry.benchmarkKind.name}."
        }
        if (champion.metricName != telemetry.metricName) {
            notes += "Incomparable metric: champion ${champion.metricName} vs run ${telemetry.metricName}."
        }
        if (champion.higherIsBetter != telemetry.higherIsBetter) {
            notes += "Metric direction differs from the champion; refusing to compare."
        }
        if (!telemetry.redactionApplied) {
            notes += "Telemetry was stored without redaction; refuse to promote on unverified data."
        }
        if (telemetry.tokensUsed <= 0L) {
            notes += "Run reported no token usage; the cost dimension cannot be verified."
        }
        if (notes.isNotEmpty()) {
            val report = baseReport(
                telemetry, champion, score, champion.toDiagnosisBaseline().primaryMetric,
                notes, PromotionVerdict.BLOCKED, nowEpochMs,
            )
            return PromotionGateOutcome.Blocked(report)
        }

        // --- quality comparison ---------------------------------------------------------------
        val championScore = champion.score
        val qualityDelta = if (telemetry.higherIsBetter) score.value - championScore else championScore - score.value
        val magnitude = kotlin.math.max(abs(championScore), 1e-9)
        val relativeDelta = qualityDelta / magnitude

        val candidatePace = score.tokensPerAction
        val championPace = champion.tokensPerAction

        if (qualityDelta <= 0.0) {
            notes += "Candidate did not beat the champion " +
                "(${formatMetric(score.value)} vs ${formatMetric(championScore)})."
            val report = baseReport(
                telemetry, champion, score, championScore, notes, PromotionVerdict.REJECTED, nowEpochMs,
            )
            return PromotionGateOutcome.Rejected(report)
        }
        if (relativeDelta < minimumQualityGainRelative) {
            notes += "Quality gain ${percent(relativeDelta)} is below the declared " +
                "${percent(minimumQualityGainRelative)} threshold; too small to claim."
            val report = baseReport(
                telemetry, champion, score, championScore, notes, PromotionVerdict.INCONCLUSIVE, nowEpochMs,
            )
            return PromotionGateOutcome.Inconclusive(report)
        }
        if (candidatePace != null && championPace != null && championPace > 0.0) {
            val ratio = candidatePace / championPace
            if (ratio > 1.0 + costEnvelopeTolerance) {
                notes += "Quality improved but token pace rose ${percent(ratio - 1.0)}, beyond the " +
                    "${percent(costEnvelopeTolerance)} cost envelope."
                val report = baseReport(
                    telemetry, champion, score, championScore, notes, PromotionVerdict.REJECTED, nowEpochMs,
                )
                return PromotionGateOutcome.Rejected(report)
            }
        }

        notes += "Eligible: ${formatMetric(score.value)} vs champion ${formatMetric(championScore)} " +
            "(${percent(relativeDelta)} better)."

        if (!approvedByOwner) {
            val report = baseReport(
                telemetry, champion, score, championScore, notes,
                PromotionVerdict.AWAITING_OWNER_APPROVAL, nowEpochMs,
            )
            return PromotionGateOutcome.AwaitingOwnerApproval(report)
        }

        val awaiting = baseReport(
            telemetry, champion, score, championScore, notes, PromotionVerdict.PROMOTED, nowEpochMs,
        )
        val stored = persistReport(awaiting, nowEpochMs)
        val newChampion = ledger.promote(telemetry, stored.reportId, champion, nowEpochMs)
        return PromotionGateOutcome.Promoted(stored, newChampion)
    }

    /**
     * Applies the owner's decision to a report the gate already produced.
     *
     * This deliberately does NOT re-run the comparison: the numbers the owner looked at are the
     * numbers that get promoted, and a champion that moved in the meantime invalidates the report
     * instead of silently re-targeting it.
     */
    fun applyOwnerDecision(
        reportId: String,
        approvedByOwner: Boolean,
        telemetryLookup: (String) -> RunTelemetry?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): PromotionGateOutcome {
        val report = getReport(reportId)
            ?: return PromotionGateOutcome.Blocked(
                PromotionReport(
                    reportId = reportId,
                    competitionSlug = "",
                    candidateTelemetryId = "",
                    candidateVersionRef = "",
                    championVersionRef = null,
                    benchmarkKind = BenchmarkKind.SCALAR_METRIC,
                    evaluatorId = null,
                    metricName = "",
                    higherIsBetter = true,
                    candidateScore = null,
                    championScore = null,
                    qualityDelta = null,
                    relativeQualityDelta = null,
                    candidateTokensPerAction = null,
                    championTokensPerAction = null,
                    tokenPaceDeltaPercent = null,
                    minimumQualityGainRelative = minimumQualityGainRelative,
                    costEnvelopeTolerance = costEnvelopeTolerance,
                    passesHardGates = false,
                    notes = listOf("Promotion report '$reportId' does not exist."),
                    verdict = PromotionVerdict.BLOCKED,
                    createdAtEpochMs = nowEpochMs,
                ),
            )

        if (report.verdict != PromotionVerdict.AWAITING_OWNER_APPROVAL) {
            return PromotionGateOutcome.Blocked(
                report.copy(notes = report.notes + "Report is '${report.verdict.label}', so no owner decision applies."),
            )
        }
        if (!approvedByOwner) {
            val declined = report.copy(
                verdict = PromotionVerdict.REJECTED,
                notes = report.notes + "Owner declined promotion; champion retained.",
                decidesAtEpochMs = nowEpochMs,
            )
            return PromotionGateOutcome.Rejected(persistReport(declined, nowEpochMs))
        }

        val champion = ledger.activeChampion(report.competitionSlug)
        if (champion == null || champion.versionRef != report.championVersionRef) {
            val stale = report.copy(
                verdict = PromotionVerdict.INCONCLUSIVE,
                notes = report.notes + "Champion changed since this report was produced; re-run the comparison.",
                decidesAtEpochMs = nowEpochMs,
            )
            return PromotionGateOutcome.Inconclusive(persistReport(stale, nowEpochMs))
        }
        val telemetry = telemetryLookup(report.candidateTelemetryId)
        if (telemetry == null) {
            val missing = report.copy(
                verdict = PromotionVerdict.BLOCKED,
                notes = report.notes + "Candidate telemetry is no longer stored; cannot promote.",
                decidesAtEpochMs = nowEpochMs,
            )
            return PromotionGateOutcome.Blocked(persistReport(missing, nowEpochMs))
        }

        val promoted = report.copy(
            verdict = PromotionVerdict.PROMOTED,
            decidesAtEpochMs = nowEpochMs,
            notes = report.notes + "Owner approved promotion.",
        )
        val stored = persistReport(promoted, nowEpochMs)
        val newChampion = ledger.promote(telemetry, stored.reportId, champion, nowEpochMs)
        return PromotionGateOutcome.Promoted(stored, newChampion)
    }

    fun getReport(reportId: String): PromotionReport? {
        val row = database.backend.get(NS_REPORTS, reportId) ?: return null
        return runCatching { PromotionReport.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun listReports(competitionSlug: String, limit: Int = 20): List<PromotionReport> =
        database.backend.listByNamespace(NS_REPORTS)
            .mapNotNull { row ->
                runCatching { PromotionReport.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }
            .filter { it.competitionSlug == competitionSlug }
            .sortedByDescending { it.createdAtEpochMs }
            .take(limit)

    private fun baseReport(
        telemetry: RunTelemetry,
        champion: ChampionRecord?,
        score: EvaluatedScore?,
        championScore: Double?,
        notes: List<String>,
        verdict: PromotionVerdict,
        nowEpochMs: Long,
    ): PromotionReport {
        val qualityDelta = if (score != null && championScore != null) {
            if (telemetry.higherIsBetter) score.value - championScore else championScore - score.value
        } else {
            null
        }
        val relative = qualityDelta?.div(kotlin.math.max(abs(championScore ?: 0.0), 1e-9))
        val candidatePace = score?.tokensPerAction
        val championPace = champion?.tokensPerAction
        val paceDeltaPercent = if (candidatePace != null && championPace != null && championPace > 0.0) {
            (candidatePace / championPace - 1.0) * 100.0
        } else {
            null
        }
        return persistReport(
            PromotionReport(
                reportId = reportIdFactory(),
                competitionSlug = telemetry.competitionSlug,
                candidateTelemetryId = telemetry.telemetryId,
                candidateVersionRef = telemetry.versionRef,
                championVersionRef = champion?.versionRef,
                benchmarkKind = telemetry.benchmarkKind,
                evaluatorId = score?.evaluatorId,
                metricName = telemetry.metricName,
                higherIsBetter = telemetry.higherIsBetter,
                candidateScore = score?.value,
                championScore = championScore,
                qualityDelta = qualityDelta,
                relativeQualityDelta = relative,
                candidateTokensPerAction = candidatePace,
                championTokensPerAction = championPace,
                tokenPaceDeltaPercent = paceDeltaPercent,
                minimumQualityGainRelative = minimumQualityGainRelative,
                costEnvelopeTolerance = costEnvelopeTolerance,
                passesHardGates = verdict !in setOf(PromotionVerdict.BLOCKED, PromotionVerdict.REJECTED),
                notes = notes,
                verdict = verdict,
                createdAtEpochMs = nowEpochMs,
            ),
            nowEpochMs,
        )
    }

    private fun persistReport(report: PromotionReport, nowEpochMs: Long): PromotionReport {
        database.backend.upsert(
            namespace = NS_REPORTS,
            id = report.reportId,
            payloadJson = JsonCodec.stringify(report.toJson()),
            updatedAtEpochMs = nowEpochMs,
        )
        return report
    }

    private fun percent(fraction: Double): String = String.format(Locale.US, "%+.2f%%", fraction * 100.0)

    companion object {
        const val NS_REPORTS = "promotion_reports"
    }
}
