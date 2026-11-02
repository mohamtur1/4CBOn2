package io.github.mohamtur1.fourcbon2

/**
 * Division 44 (v0.3) — Fixed benchmark evaluator registry.
 *
 * Division 39's evaluator could only read `.csv` fold scores. v0.3 needs a *fixed*, owner-installed
 * evaluator per benchmark family, because the whole promotion gate depends on the candidate never
 * being able to grade itself. This registry is deliberately closed:
 *
 *  - The set of evaluators is compiled in. A candidate diff cannot register, replace, or tune one.
 *  - An unknown [BenchmarkKind] or a metric name that does not match the family is REJECTED rather
 *    than scored with a fallback formula.
 *  - Evaluation is pure and deterministic: identical telemetry always yields the identical score.
 */
enum class MetricDirection(val label: String) {
    HIGHER_IS_BETTER("higher is better"),
    LOWER_IS_BETTER("lower is better"),
}

/** The single number the promotion gate compares, plus the honesty metadata behind it. */
data class EvaluatedScore(
    val benchmarkKind: BenchmarkKind,
    val evaluatorId: String,
    val metricName: String,
    val value: Double,
    val direction: MetricDirection,
    val levelsCompleted: Int,
    val totalActions: Int,
    val tokensUsed: Long,
    val tokensPerAction: Double?,
    val evaluatedAtEpochMs: Long,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "benchmarkKind" to JsonValue.Str(benchmarkKind.name),
            "evaluatorId" to JsonValue.Str(evaluatorId),
            "metricName" to JsonValue.Str(metricName),
            "value" to JsonValue.Num(value.coerceFinite()),
            "direction" to JsonValue.Str(direction.name),
            "levelsCompleted" to JsonValue.Num(levelsCompleted.toDouble(), levelsCompleted.toString()),
            "totalActions" to JsonValue.Num(totalActions.toDouble(), totalActions.toString()),
            "tokensUsed" to JsonValue.Num(tokensUsed.toDouble(), tokensUsed.toString()),
            "tokensPerAction" to (tokensPerAction?.let { JsonValue.Num(it) } ?: JsonValue.Null),
            "evaluatedAtEpochMs" to JsonValue.Num(evaluatedAtEpochMs.toDouble(), evaluatedAtEpochMs.toString()),
        ),
    )
}

sealed interface EvaluationOutcome {
    data class Computed(val score: EvaluatedScore) : EvaluationOutcome

    /** The run exists but cannot support a promotion claim (too few actions, no levels, partial run). */
    data class Inconclusive(val reason: String, val detail: String) : EvaluationOutcome

    /** The record is structurally unusable or the evaluator does not recognise it. */
    data class Rejected(val reason: String, val detail: String) : EvaluationOutcome
}

/** A fixed, candidate-immutable scorer for one benchmark family. */
interface BenchmarkEvaluator {
    val evaluatorId: String
    val benchmarkKind: BenchmarkKind
    val acceptedMetricNames: Set<String>
    val direction: MetricDirection

    fun evaluate(telemetry: RunTelemetry): EvaluationOutcome
}

/**
 * Shared validation for every evaluator: contract version, finite metric, matching metric name,
 * and a minimum amount of observed behaviour before a number means anything.
 */
abstract class FixedBenchmarkEvaluator(
    private val minActionsForSignificance: Int = 10,
    private val minLevelsForSignificance: Int = 1,
) : BenchmarkEvaluator {
    protected abstract fun scoreOf(telemetry: RunTelemetry): Double

    override fun evaluate(telemetry: RunTelemetry): EvaluationOutcome {
        if (telemetry.contractVersion != RunTelemetry.CONTRACT_VERSION) {
            return EvaluationOutcome.Rejected(
                "unsupported_contract",
                "Telemetry contract '${telemetry.contractVersion}' is not '${RunTelemetry.CONTRACT_VERSION}'.",
            )
        }
        if (telemetry.benchmarkKind != benchmarkKind) {
            return EvaluationOutcome.Rejected(
                "benchmark_mismatch",
                "Evaluator '$evaluatorId' handles ${benchmarkKind.name}, not ${telemetry.benchmarkKind.name}.",
            )
        }
        if (telemetry.metricName !in acceptedMetricNames) {
            return EvaluationOutcome.Rejected(
                "unknown_metric",
                "Metric '${telemetry.metricName}' is not one of ${acceptedMetricNames.sorted()}.",
            )
        }
        if (!telemetry.primaryMetric.isFinite()) {
            return EvaluationOutcome.Rejected("non_finite_metric", "Primary metric is not a finite number.")
        }
        if (!telemetry.isComplete) {
            return EvaluationOutcome.Inconclusive(
                "incomplete_run",
                "Run has no completion timestamp; an unfinished run cannot earn a promotion.",
            )
        }
        if (telemetry.totalActions < minActionsForSignificance) {
            return EvaluationOutcome.Inconclusive(
                "too_few_actions",
                "Run reported ${telemetry.totalActions} actions; at least $minActionsForSignificance are required.",
            )
        }
        if (minLevelsForSignificance > 0 && telemetry.levelsCompleted < minLevelsForSignificance) {
            return EvaluationOutcome.Inconclusive(
                "no_levels_completed",
                "Run completed ${telemetry.levelsCompleted} levels; at least $minLevelsForSignificance required.",
            )
        }
        val score = scoreOf(telemetry)
        if (!score.isFinite()) {
            return EvaluationOutcome.Rejected("non_finite_score", "Evaluator produced a non-finite score.")
        }
        return EvaluationOutcome.Computed(
            EvaluatedScore(
                benchmarkKind = benchmarkKind,
                evaluatorId = evaluatorId,
                metricName = telemetry.metricName,
                value = score,
                direction = direction,
                levelsCompleted = telemetry.levelsCompleted,
                totalActions = telemetry.totalActions,
                tokensUsed = telemetry.tokensUsed,
                tokensPerAction = telemetry.tokensPerAction,
                evaluatedAtEpochMs = telemetry.recordedAtEpochMs,
            ),
        )
    }
}

/**
 * ARC-AGI-3 interactive evaluator. The score is the platform's own leaderboard score; token pace is
 * kept alongside it as the cost dimension the gate measures separately.
 *
 * Thresholds mirror the live campaign evidence in the v0.3 handoff: a run must complete at least one
 * level and spend at least 10 actions before it can out-rank a locked champion.
 */
class ArcAgiInteractiveEvaluator(
    minActionsForSignificance: Int = 10,
    minLevelsForSignificance: Int = 1,
) : FixedBenchmarkEvaluator(minActionsForSignificance, minLevelsForSignificance) {
    override val evaluatorId: String = "eval.arc_agi_interactive.v1"
    override val benchmarkKind: BenchmarkKind = BenchmarkKind.ARC_AGI_INTERACTIVE
    override val acceptedMetricNames: Set<String> = setOf("ARC-AGI-3 score", "score", "leaderboard_score")
    override val direction: MetricDirection = MetricDirection.HIGHER_IS_BETTER

    override fun scoreOf(telemetry: RunTelemetry): Double = telemetry.primaryMetric
}

/**
 * Tabular evaluator kept from Division 37/39 so v0.2 workflows keep working unchanged: the metric is
 * cross-validation RMSE exported into the diagnostics map by the prepare-only pipeline.
 */
class TabularCvRmseEvaluator(
    minActionsForSignificance: Int = 1,
    minLevelsForSignificance: Int = 1,
) : FixedBenchmarkEvaluator(minActionsForSignificance, minLevelsForSignificance) {
    override val evaluatorId: String = "eval.tabular_cv_rmse.v1"
    override val benchmarkKind: BenchmarkKind = BenchmarkKind.TABULAR_CSV_CV
    override val acceptedMetricNames: Set<String> = setOf("CV_RMSE", "cv_rmse", "rmse")
    override val direction: MetricDirection = MetricDirection.LOWER_IS_BETTER

    override fun scoreOf(telemetry: RunTelemetry): Double {
        val exported = telemetry.diagnostics["mean_cv_rmse"]?.toDoubleOrNull()
        return exported ?: telemetry.primaryMetric
    }
}

/** Generic scalar challenge (AUC, MAE, BLEU, …) with the direction supplied by the run itself. */
class ScalarMetricEvaluator(
    minActionsForSignificance: Int = 1,
    minLevelsForSignificance: Int = 1,
) : FixedBenchmarkEvaluator(minActionsForSignificance, minLevelsForSignificance) {
    override val evaluatorId: String = "eval.scalar_metric.v1"
    override val benchmarkKind: BenchmarkKind = BenchmarkKind.SCALAR_METRIC
    override val acceptedMetricNames: Set<String> = setOf(
        "score", "auc", "roc_auc", "accuracy", "f1", "mae", "rmse", "logloss", "bleu",
    )

    // Direction is a property of the competition, not of the evaluator; it is read per run.
    override val direction: MetricDirection = MetricDirection.HIGHER_IS_BETTER

    override fun evaluate(telemetry: RunTelemetry): EvaluationOutcome {
        val outcome = super.evaluate(telemetry)
        if (outcome !is EvaluationOutcome.Computed) return outcome
        val direction = if (telemetry.higherIsBetter) MetricDirection.HIGHER_IS_BETTER else MetricDirection.LOWER_IS_BETTER
        return EvaluationOutcome.Computed(outcome.score.copy(direction = direction))
    }

    override fun scoreOf(telemetry: RunTelemetry): Double = telemetry.primaryMetric
}

/** Owner-installed, candidate-immutable lookup. */
class FixedEvaluatorRegistry(
    evaluators: List<BenchmarkEvaluator> = listOf(
        ArcAgiInteractiveEvaluator(),
        TabularCvRmseEvaluator(),
        ScalarMetricEvaluator(),
    ),
) {
    private val byKind: Map<BenchmarkKind, BenchmarkEvaluator> = evaluators.associateBy { it.benchmarkKind }
    private val frozenIds: Set<String> = evaluators.map { it.evaluatorId }.toSet()

    val installedEvaluatorIds: List<String> get() = frozenIds.sorted()

    fun evaluatorFor(kind: BenchmarkKind): BenchmarkEvaluator? = byKind[kind]

    fun evaluatesWith(evaluatorId: String): BenchmarkEvaluator? =
        byKind.values.firstOrNull { it.evaluatorId == evaluatorId }

    /** Rejects any candidate patch that tries to swap, disable, or re-tune the scoring authority. */
    fun rejectsEvaluatorMutation(requestedEvaluatorId: String?): String? {
        if (requestedEvaluatorId == null) return null
        if (requestedEvaluatorId in frozenIds) return null
        return "Candidate may not install evaluator '$requestedEvaluatorId'; the evaluator set is owner-installed."
    }

    fun evaluate(telemetry: RunTelemetry): EvaluationOutcome {
        val evaluator = evaluatorFor(telemetry.benchmarkKind)
            ?: return EvaluationOutcome.Rejected(
                "no_evaluator",
                "No fixed evaluator is installed for ${telemetry.benchmarkKind.name}.",
            )
        return evaluator.evaluate(telemetry)
    }
}
