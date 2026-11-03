package io.github.mohamtur1.fourcbon2

import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/**
 * Division 45 (v0.3) — Run diagnoser.
 *
 * The diagnoser is the "observe → diagnose" half of the boss-worker loop in `AgentLab_Blueprint.md`.
 * It compares one ingested run against the locked champion and emits *ranked findings*, each with the
 * exact numbers behind it and exactly one bounded lever that could address it.
 *
 * Hard rules:
 *  - Deterministic: same inputs → same findings, same order. No clocks, no randomness inside rules.
 *  - Explainable: every finding carries at least one evidence line containing the raw numbers.
 *  - Conservative: if the run is too small to judge, the diagnoser says so instead of inventing a
 *    direction of change (see [FindingKind.TELEMETRY_UNUSABLE] / [FindingKind.INSUFFICIENT_EVIDENCE]).
 */

enum class FindingSeverity(val rank: Int, val label: String) {
    CRITICAL(4, "Critical"),
    HIGH(3, "High"),
    MEDIUM(2, "Medium"),
    LOW(1, "Low"),
    INFO(0, "Informational"),
}

enum class FindingKind(val title: String) {
    SCORE_REGRESSION("Score regression"),
    LEVEL_STALL("Level stall"),
    TOKEN_BURN("Token burn"),
    ACTION_INEFFICIENCY("Action inefficiency"),
    WALL_CLOCK_INFLATION("Run-time inflation"),
    TELEMETRY_UNUSABLE("Unusable telemetry"),
    INSUFFICIENT_EVIDENCE("Insufficient evidence"),
    CHAMPION_ABSENT("No locked champion"),
    STABLE("Stable versus champion"),
}

/** The one bounded change family a finding points at. A proposal uses exactly one of these. */
enum class CandidateLever(val label: String) {
    REASONING_TOKEN_BUDGET("Reasoning token budget"),
    EXPLORATION_POLICY("Frontier exploration policy"),
    ACTION_PRUNING("Action pruning"),
    WALL_CLOCK_BUDGET("Wall-clock budget"),
    REVERT_TO_CHAMPION("Revert to champion configuration"),
    NONE("No bounded change justified"),
}

data class DiagnosisFinding(
    val kind: FindingKind,
    val severity: FindingSeverity,
    val lever: CandidateLever,
    val summary: String,
    val evidence: List<String>,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "kind" to JsonValue.Str(kind.name),
            "severity" to JsonValue.Str(severity.name),
            "lever" to JsonValue.Str(lever.name),
            "summary" to JsonValue.Str(summary),
            "evidence" to JsonValue.Arr(evidence.map { JsonValue.Str(it) }),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): DiagnosisFinding = DiagnosisFinding(
            kind = runCatching { FindingKind.valueOf(obj.stringOrDefault("kind")) }.getOrDefault(FindingKind.STABLE),
            severity = runCatching { FindingSeverity.valueOf(obj.stringOrDefault("severity")) }
                .getOrDefault(FindingSeverity.INFO),
            lever = runCatching { CandidateLever.valueOf(obj.stringOrDefault("lever")) }.getOrDefault(CandidateLever.NONE),
            summary = obj.stringOrDefault("summary"),
            evidence = obj.stringList("evidence"),
        )
    }
}

/** Everything the rules are allowed to look at. Pure data, so diagnosis is unit-testable. */
data class DiagnosisBaseline(
    val versionRef: String,
    val metricName: String,
    val primaryMetric: Double,
    val higherIsBetter: Boolean,
    val tokensPerAction: Double?,
    val actionsPerLevel: Double?,
    val levelsCompleted: Int,
    val totalActions: Int,
    val wallClockMs: Long,
)

data class DiagnosisReport(
    val reportId: String,
    val telemetryId: String,
    val competitionSlug: String,
    val runLabel: String,
    val baselineVersionRef: String?,
    val findings: List<DiagnosisFinding>,
    val scoreDelta: Double?,
    val tokenPaceDeltaPercent: Double?,
    val createdAtEpochMs: Long,
    val contractVersion: String = CONTRACT_VERSION,
) {
    /** Most severe finding, used to pick the single lever for the next candidate. */
    val primaryFinding: DiagnosisFinding?
        get() = findings.firstOrNull()

    val recommendedLever: CandidateLever
        get() = primaryFinding?.lever ?: CandidateLever.NONE

    val requiresOwnerVisibleAlert: Boolean
        get() = findings.any { it.severity.rank >= FindingSeverity.HIGH.rank }

    fun headline(): String {
        val primary = primaryFinding ?: return "No diagnosis available"
        return "${primary.severity.label}: ${primary.title()}${if (findings.size > 1) " (+${findings.size - 1} more)" else ""}"
    }

    private fun DiagnosisFinding.title(): String = kind.title

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "contractVersion" to JsonValue.Str(contractVersion),
            "reportId" to JsonValue.Str(reportId),
            "telemetryId" to JsonValue.Str(telemetryId),
            "competitionSlug" to JsonValue.Str(competitionSlug),
            "runLabel" to JsonValue.Str(runLabel),
            "baselineVersionRef" to (baselineVersionRef?.let(JsonValue::Str) ?: JsonValue.Null),
            "findings" to JsonValue.Arr(findings.map { it.toJson() }),
            "scoreDelta" to (scoreDelta?.let { JsonValue.Num(it) } ?: JsonValue.Null),
            "tokenPaceDeltaPercent" to (tokenPaceDeltaPercent?.let { JsonValue.Num(it) } ?: JsonValue.Null),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
        ),
    )

    companion object {
        const val CONTRACT_VERSION = "4cbon2.diagnosis_report.v1"

        fun fromJson(obj: JsonValue.Obj): DiagnosisReport = DiagnosisReport(
            reportId = obj.stringOrDefault("reportId"),
            telemetryId = obj.stringOrDefault("telemetryId"),
            competitionSlug = obj.stringOrDefault("competitionSlug"),
            runLabel = obj.stringOrDefault("runLabel"),
            baselineVersionRef = obj.string("baselineVersionRef"),
            findings = obj.array("findings")
                .mapNotNull { (it as? JsonValue.Obj)?.let(DiagnosisFinding::fromJson) },
            scoreDelta = (obj.entries["scoreDelta"] as? JsonValue.Num)?.value,
            tokenPaceDeltaPercent = (obj.entries["tokenPaceDeltaPercent"] as? JsonValue.Num)?.value,
            createdAtEpochMs = obj.long("createdAtEpochMs", 0L),
            contractVersion = obj.stringOrDefault("contractVersion", CONTRACT_VERSION),
        )
    }
}

/** Owner-tunable thresholds. Defaults are documented with the live campaign evidence that set them. */
data class DiagnosisThresholds(
    /** `r11l` burned 96,609 tokens for 53 actions; +25% pace over champion is treated as burn. */
    val tokenPaceBurnRatio: Double = 1.25,
    /** A score move smaller than 2% of the champion is treated as noise, not a regression. */
    val scoreRegressionRelativeTolerance: Double = 0.02,
    /** Above 3× the tolerance the regression is escalated to CRITICAL. */
    val scoreRegressionCriticalMultiple: Double = 3.0,
    /** More than 1.5× the champion's actions-per-level means wasted actions. */
    val actionEfficiencyRatio: Double = 1.5,
    /** More than 1.5× the champion's wall clock means runaway run time. */
    val wallClockInflationRatio: Double = 1.5,
    /** Runs smaller than this cannot support a diagnosis at all. */
    val minActionsForDiagnosis: Int = 10,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "tokenPaceBurnRatio" to JsonValue.Num(tokenPaceBurnRatio),
            "scoreRegressionRelativeTolerance" to JsonValue.Num(scoreRegressionRelativeTolerance),
            "scoreRegressionCriticalMultiple" to JsonValue.Num(scoreRegressionCriticalMultiple),
            "actionEfficiencyRatio" to JsonValue.Num(actionEfficiencyRatio),
            "wallClockInflationRatio" to JsonValue.Num(wallClockInflationRatio),
            "minActionsForDiagnosis" to JsonValue.Num(minActionsForDiagnosis.toDouble(), minActionsForDiagnosis.toString()),
        ),
    )

    companion object {
        val DEFAULT = DiagnosisThresholds()

        fun fromJson(obj: JsonValue.Obj): DiagnosisThresholds = DiagnosisThresholds(
            tokenPaceBurnRatio = obj.double("tokenPaceBurnRatio", 1.25),
            scoreRegressionRelativeTolerance = obj.double("scoreRegressionRelativeTolerance", 0.02),
            scoreRegressionCriticalMultiple = obj.double("scoreRegressionCriticalMultiple", 3.0),
            actionEfficiencyRatio = obj.double("actionEfficiencyRatio", 1.5),
            wallClockInflationRatio = obj.double("wallClockInflationRatio", 1.5),
            minActionsForDiagnosis = obj.int("minActionsForDiagnosis", 10),
        )
    }
}

class RunDiagnoser(
    private val thresholds: DiagnosisThresholds = DiagnosisThresholds.DEFAULT,
    private val reportIdFactory: () -> String = { "diag_${UUID.randomUUID().toString().take(8)}" },
) {
    fun diagnose(
        telemetry: RunTelemetry,
        baseline: DiagnosisBaseline?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DiagnosisReport {
        val findings = mutableListOf<DiagnosisFinding>()

        if (!telemetry.isScorable || telemetry.totalActions < thresholds.minActionsForDiagnosis) {
            findings += DiagnosisFinding(
                kind = FindingKind.TELEMETRY_UNUSABLE,
                severity = FindingSeverity.CRITICAL,
                lever = CandidateLever.NONE,
                summary = "This run cannot be scored, so no change can be justified from it.",
                evidence = listOf(
                    "metric=${telemetry.metricName}=${formatMetric(telemetry.primaryMetric)}",
                    "actions=${telemetry.totalActions} (minimum ${thresholds.minActionsForDiagnosis})",
                    "levels=${telemetry.levelsCompleted}",
                ),
            )
            return buildReport(telemetry, baseline, findings, null, null, nowEpochMs)
        }

        if (baseline == null) {
            findings += DiagnosisFinding(
                kind = FindingKind.CHAMPION_ABSENT,
                severity = FindingSeverity.INFO,
                lever = CandidateLever.NONE,
                summary = "No champion is locked for this competition, so this run has nothing to beat.",
                evidence = listOf("Lock this run as champion before proposing an experiment."),
            )
            return buildReport(telemetry, null, findings, null, null, nowEpochMs)
        }

        val scoreDelta = qualityDelta(telemetry.primaryMetric, baseline.primaryMetric, telemetry.higherIsBetter)
        val championMagnitude = max(abs(baseline.primaryMetric), 1e-9)
        val relativeScoreDelta = scoreDelta / championMagnitude

        if (relativeScoreDelta < -thresholds.scoreRegressionRelativeTolerance) {
            val critical = relativeScoreDelta <= -thresholds.scoreRegressionRelativeTolerance *
                thresholds.scoreRegressionCriticalMultiple
            findings += DiagnosisFinding(
                kind = FindingKind.SCORE_REGRESSION,
                severity = if (critical) FindingSeverity.CRITICAL else FindingSeverity.HIGH,
                lever = CandidateLever.REVERT_TO_CHAMPION,
                summary = "The run scored below champion ${baseline.versionRef} by ${percent(abs(relativeScoreDelta))}.",
                evidence = listOf(
                    "candidate ${telemetry.metricName}=${formatMetric(telemetry.primaryMetric)} " +
                        "vs champion ${formatMetric(baseline.primaryMetric)}",
                    "delta=${formatMetric(scoreDelta)} (${percent(relativeScoreDelta)} relative)",
                    "tolerance=${percent(thresholds.scoreRegressionRelativeTolerance)}",
                ),
            )
        }

        if (telemetry.levelsCompleted == 0 && baseline.levelsCompleted > 0) {
            findings += DiagnosisFinding(
                kind = FindingKind.LEVEL_STALL,
                severity = FindingSeverity.CRITICAL,
                lever = CandidateLever.REVERT_TO_CHAMPION,
                summary = "The run completed no levels while the champion completed ${baseline.levelsCompleted}.",
                evidence = listOf(
                    "candidate levels=0 after ${telemetry.totalActions} actions",
                    "champion levels=${baseline.levelsCompleted} " +
                        "(actions/level=${formatMetric(baseline.actionsPerLevel ?: 0.0)})",
                ),
            )
        }

        val candidatePace = telemetry.tokensPerAction
        val championPace = baseline.tokensPerAction
        var tokenPaceDeltaPercent: Double? = null
        if (candidatePace != null && championPace != null && championPace > 0.0) {
            val ratio = candidatePace / championPace
            tokenPaceDeltaPercent = (ratio - 1.0) * 100.0
            if (ratio > thresholds.tokenPaceBurnRatio) {
                findings += DiagnosisFinding(
                    kind = FindingKind.TOKEN_BURN,
                    severity = if (ratio > thresholds.tokenPaceBurnRatio * 2) FindingSeverity.HIGH else FindingSeverity.MEDIUM,
                    lever = CandidateLever.REASONING_TOKEN_BUDGET,
                    summary = "Each action costs ${percent(ratio - 1.0)} more reasoning tokens than the champion.",
                    evidence = listOf(
                        "candidate ${telemetry.tokensUsed} tokens / ${telemetry.totalActions} actions " +
                            "= ${formatMetric(candidatePace)} tokens/action",
                        "champion ${formatMetric(championPace)} tokens/action",
                        "burn threshold=${percent(thresholds.tokenPaceBurnRatio - 1.0)} above champion",
                    ),
                )
            }
        }

        val candidateActionsPerLevel = telemetry.actionsPerLevel
        val championActionsPerLevel = baseline.actionsPerLevel
        if (candidateActionsPerLevel != null && championActionsPerLevel != null && championActionsPerLevel > 0.0) {
            val ratio = candidateActionsPerLevel / championActionsPerLevel
            if (ratio > thresholds.actionEfficiencyRatio) {
                findings += DiagnosisFinding(
                    kind = FindingKind.ACTION_INEFFICIENCY,
                    severity = FindingSeverity.MEDIUM,
                    lever = CandidateLever.ACTION_PRUNING,
                    summary = "The run needed ${percent(ratio - 1.0)} more actions per level than the champion.",
                    evidence = listOf(
                        "candidate ${formatMetric(candidateActionsPerLevel)} actions/level " +
                            "(${telemetry.totalActions} actions, ${telemetry.levelsCompleted} levels)",
                        "champion ${formatMetric(championActionsPerLevel)} actions/level",
                    ),
                )
            }
        }

        if (baseline.wallClockMs > 0 && telemetry.wallClockMs > 0) {
            val ratio = telemetry.wallClockMs.toDouble() / baseline.wallClockMs.toDouble()
            if (ratio > thresholds.wallClockInflationRatio) {
                findings += DiagnosisFinding(
                    kind = FindingKind.WALL_CLOCK_INFLATION,
                    severity = FindingSeverity.LOW,
                    lever = CandidateLever.WALL_CLOCK_BUDGET,
                    summary = "The run took ${percent(ratio - 1.0)} longer than the champion.",
                    evidence = listOf(
                        "candidate ${telemetry.wallClockMs} ms vs champion ${baseline.wallClockMs} ms",
                    ),
                )
            }
        }

        if (findings.isEmpty()) {
            val atLeastAsGood = relativeScoreDelta >= 0.0
            findings += DiagnosisFinding(
                kind = if (atLeastAsGood) FindingKind.STABLE else FindingKind.INSUFFICIENT_EVIDENCE,
                severity = FindingSeverity.INFO,
                lever = CandidateLever.NONE,
                summary = if (atLeastAsGood) {
                    "Nothing to fix: the run is within noise of the champion on score, cost, and level pace."
                } else {
                    "The small score drop is inside the noise tolerance, so it is inconclusive rather than a regression."
                },
                evidence = listOf(
                    "score delta=${formatMetric(scoreDelta)} (${percent(relativeScoreDelta)} relative)",
                    "tolerance=${percent(thresholds.scoreRegressionRelativeTolerance)}",
                ),
            )
        }

        return buildReport(telemetry, baseline, findings, scoreDelta, tokenPaceDeltaPercent, nowEpochMs)
    }

    private fun buildReport(
        telemetry: RunTelemetry,
        baseline: DiagnosisBaseline?,
        findings: List<DiagnosisFinding>,
        scoreDelta: Double?,
        tokenPaceDeltaPercent: Double?,
        nowEpochMs: Long,
    ): DiagnosisReport = DiagnosisReport(
        reportId = reportIdFactory(),
        telemetryId = telemetry.telemetryId,
        competitionSlug = telemetry.competitionSlug,
        runLabel = telemetry.runLabel,
        baselineVersionRef = baseline?.versionRef,
        findings = rank(findings),
        scoreDelta = scoreDelta,
        tokenPaceDeltaPercent = tokenPaceDeltaPercent,
        createdAtEpochMs = nowEpochMs,
    )

    /**
     * Deterministic ranking: severity first, then a fixed kind priority so equal severities never
     * reorder between runs (score problems outrank cost problems outrank cosmetic problems).
     */
    private fun rank(findings: List<DiagnosisFinding>): List<DiagnosisFinding> =
        findings.sortedWith(
            compareByDescending<DiagnosisFinding> { it.severity.rank }
                .thenBy { KIND_PRIORITY.indexOf(it.kind).let { idx -> if (idx < 0) 99 else idx } }
                .thenBy { it.kind.name },
        )

    /** Positive = candidate better than champion, regardless of metric direction. */
    private fun qualityDelta(candidate: Double, champion: Double, higherIsBetter: Boolean): Double =
        if (higherIsBetter) candidate - champion else champion - candidate

    private fun percent(fraction: Double): String =
        String.format(Locale.US, "%+.1f%%", fraction * 100.0)

    companion object {
        private val KIND_PRIORITY = listOf(
            FindingKind.SCORE_REGRESSION,
            FindingKind.LEVEL_STALL,
            FindingKind.TOKEN_BURN,
            FindingKind.ACTION_INEFFICIENCY,
            FindingKind.WALL_CLOCK_INFLATION,
            FindingKind.TELEMETRY_UNUSABLE,
            FindingKind.CHAMPION_ABSENT,
            FindingKind.INSUFFICIENT_EVIDENCE,
            FindingKind.STABLE,
        )
    }
}
