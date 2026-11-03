package io.github.mohamtur1.fourcbon2

import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Division 46 (v0.3) — Bounded candidate diff synthesis.
 *
 * This is the "propose ONE versioned change" step of the blueprint loop. A diagnosis becomes at most
 * one candidate configuration diff, expressed only in the small allowlisted vocabulary below.
 *
 * Safety properties enforced here (and covered by tests):
 *  1. **One lever per experiment.** A diff may not mix a token-budget change with an exploration
 *     change; mixed diffs cannot be attributed to a cause.
 *  2. **Allowlist only.** Keys outside [BoundedPatchPolicy.ALLOWED_KEYS] are rejected, so a diff can
 *     never touch evaluator seeds, submission caps, credential handling, or promotion state.
 *  3. **Bounds only tighten.** Every numeric value is clamped into its declared range, and spend
 *     knobs may never exceed the owner-approved [ExperimentBudgetCaps]. A candidate cannot vote
 *     itself a bigger budget.
 *  4. **No diff without a finding.** If the diagnoser found nothing actionable, synthesis returns
 *     [DiffSynthesisOutcome.NoActionNeeded] instead of inventing busywork.
 */

enum class CandidateRiskClass(val label: String) {
    LOW("Low — configuration-only, reversible"),
    MEDIUM("Medium — changes how the agent explores"),
    HIGH("High — restores a previous configuration wholesale"),
}

/** Allowlisted keys plus their hard numeric ranges. Anything not listed is forbidden. */
class BoundedPatchPolicy(
    private val caps: ExperimentBudgetCaps = ExperimentBudgetCaps.OWNER_APPROVED_DEFAULT,
) {
    private val numericRanges: Map<String, ClosedFloatingPointRange<Double>> = mapOf(
        KEY_REASONING_TOKEN_BUDGET to 128.0..caps.maxTokensPerAction,
        KEY_MAX_ACTIONS_PER_LEVEL to 1.0..200.0,
        KEY_EXPLORATION_TEMPERATURE to 0.0..1.0,
        KEY_EXPLORATION_FRONTIER_WEIGHT to 0.0..1.0,
        KEY_DEAD_ACTION_PRUNE_THRESHOLD to 0.0..10.0,
        KEY_MAX_WALL_CLOCK_MS to 60_000.0..max(caps.maxWallClockMs.toDouble(), 60_000.0),
        KEY_MAX_ACTIONS to 1.0..caps.maxActions.toDouble(),
        KEY_MAX_TOTAL_TOKENS to 1.0..caps.maxTotalTokens.toDouble(),
    )

    /** The complete set of keys a candidate diff may write. Everything else is unreachable. */
    val allowedKeys: Set<String> get() = numericRanges.keys + setOf(KEY_REVERT_TO_VERSION)

    fun validate(patch: Map<String, String>): PatchValidation {
        if (patch.isEmpty()) {
            return PatchValidation(false, listOf("Patch is empty; an experiment must change exactly one thing."))
        }
        if (patch.size > MAX_KEYS_PER_DIFF) {
            return PatchValidation(
                false,
                listOf("Patch changes ${patch.size} keys; at most $MAX_KEYS_PER_DIFF are allowed per experiment."),
            )
        }
        val violations = mutableListOf<String>()
        for ((key, value) in patch) {
            if (key !in allowedKeys) {
                violations += "Key '$key' is not on the allowlist and cannot be changed by a candidate."
                continue
            }
            if (key == KEY_REVERT_TO_VERSION) {
                if (value.isBlank()) violations += "Revert target version is blank."
                continue
            }
            val numeric = value.toDoubleOrNull()
            if (numeric == null || !numeric.isFinite()) {
                violations += "Key '$key' needs a finite number, got '$value'."
                continue
            }
            val range = numericRanges.getValue(key)
            if (numeric !in range) {
                violations += "Key '$key'=$value is outside the allowed range ${range.start}..${range.endInclusive}."
            }
        }
        return PatchValidation(violations.isEmpty(), violations)
    }

    /** Explicit refusals for the categories a candidate must never be able to reach. */
    fun forbiddenAttempts(patch: Map<String, String>, requestedEvaluatorId: String?): List<String> {
        val attempts = mutableListOf<String>()
        val forbiddenPrefixes = listOf("evaluator", "submission", "credential", "secret", "promotion", "champion")
        for (key in patch.keys) {
            val normalized = key.lowercase(Locale.US)
            if (forbiddenPrefixes.any { normalized.startsWith(it) || normalized.contains("_$it") }) {
                attempts += "Key '$key' targets a protected policy area."
            }
        }
        return attempts
    }

    companion object {
        const val MAX_KEYS_PER_DIFF = 1

        /** Never allowlisted; named here only so an attempt is refused with a precise reason. */
        const val KEY_EVALUATOR_ID = "evaluator_id"

        const val KEY_REASONING_TOKEN_BUDGET = "reasoning_token_budget"
        const val KEY_MAX_ACTIONS_PER_LEVEL = "max_actions_per_level"
        const val KEY_EXPLORATION_TEMPERATURE = "exploration_temperature"
        const val KEY_EXPLORATION_FRONTIER_WEIGHT = "exploration_frontier_weight"
        const val KEY_DEAD_ACTION_PRUNE_THRESHOLD = "dead_action_prune_threshold"
        const val KEY_MAX_WALL_CLOCK_MS = "max_wall_clock_ms"
        const val KEY_MAX_ACTIONS = "max_actions"
        const val KEY_MAX_TOTAL_TOKENS = "max_total_tokens"
        const val KEY_REVERT_TO_VERSION = "revert_to_version"

        val DEFAULT = BoundedPatchPolicy()
    }
}

data class PatchValidation(val isValid: Boolean, val violations: List<String>)

/** Exactly one versioned, bounded, attributable change. */
data class BoundedCandidateDiff(
    val diffId: String,
    val competitionSlug: String,
    val baseVersionRef: String,
    val diagnosisReportId: String,
    val lever: CandidateLever,
    val hypothesis: String,
    val expectedEffect: String,
    val patch: Map<String, String>,
    val risk: CandidateRiskClass,
    val addressesFindings: List<FindingKind>,
    val plannedMaxTokensPerAction: Double,
    val createdAtEpochMs: Long,
    val contractVersion: String = CONTRACT_VERSION,
) {
    fun patchSummary(): String = patch.entries
        .sortedBy { it.key }
        .joinToString(", ") { (k, v) -> "$k=$v" }

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "contractVersion" to JsonValue.Str(contractVersion),
            "diffId" to JsonValue.Str(diffId),
            "competitionSlug" to JsonValue.Str(competitionSlug),
            "baseVersionRef" to JsonValue.Str(baseVersionRef),
            "diagnosisReportId" to JsonValue.Str(diagnosisReportId),
            "lever" to JsonValue.Str(lever.name),
            "hypothesis" to JsonValue.Str(hypothesis),
            "expectedEffect" to JsonValue.Str(expectedEffect),
            "patch" to JsonValue.Obj(patch.toSortedMap().mapValues { (_, v) -> JsonValue.Str(v) as JsonValue }),
            "risk" to JsonValue.Str(risk.name),
            "addressesFindings" to JsonValue.Arr(addressesFindings.map { JsonValue.Str(it.name) }),
            "plannedMaxTokensPerAction" to JsonValue.Num(plannedMaxTokensPerAction.coerceFinite()),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
        ),
    )

    companion object {
        const val CONTRACT_VERSION = "4cbon2.candidate_diff.v1"

        fun fromJson(obj: JsonValue.Obj): BoundedCandidateDiff = BoundedCandidateDiff(
            diffId = obj.stringOrDefault("diffId"),
            competitionSlug = obj.stringOrDefault("competitionSlug"),
            baseVersionRef = obj.stringOrDefault("baseVersionRef"),
            diagnosisReportId = obj.stringOrDefault("diagnosisReportId"),
            lever = runCatching { CandidateLever.valueOf(obj.stringOrDefault("lever")) }
                .getOrDefault(CandidateLever.NONE),
            hypothesis = obj.stringOrDefault("hypothesis"),
            expectedEffect = obj.stringOrDefault("expectedEffect"),
            patch = obj.obj("patch")?.entries
                ?.mapNotNull { (k, v) -> (v as? JsonValue.Str)?.let { k to it.value } }
                ?.toMap()
                ?: emptyMap(),
            risk = runCatching { CandidateRiskClass.valueOf(obj.stringOrDefault("risk")) }
                .getOrDefault(CandidateRiskClass.MEDIUM),
            addressesFindings = obj.stringList("addressesFindings")
                .mapNotNull { name -> runCatching { FindingKind.valueOf(name) }.getOrNull() },
            plannedMaxTokensPerAction = obj.double("plannedMaxTokensPerAction", 0.0),
            createdAtEpochMs = obj.long("createdAtEpochMs", 0L),
            contractVersion = obj.stringOrDefault("contractVersion", CONTRACT_VERSION),
        )
    }
}

sealed interface DiffSynthesisOutcome {
    data class Proposed(val diff: BoundedCandidateDiff) : DiffSynthesisOutcome

    /** The diagnosis did not justify any change — the honest answer, and the cheapest one. */
    data class NoActionNeeded(val reason: String) : DiffSynthesisOutcome

    data class Rejected(val reason: String, val detail: String, val violations: List<String> = emptyList()) : DiffSynthesisOutcome
}

class CandidateDiffSynthesizer(
    private val policy: BoundedPatchPolicy = BoundedPatchPolicy.DEFAULT,
    private val registry: FixedEvaluatorRegistry = FixedEvaluatorRegistry(),
    private val diffIdFactory: () -> String = { "diff_${UUID.randomUUID().toString().take(8)}" },
) {
    fun synthesize(
        report: DiagnosisReport,
        run: RunTelemetry,
        baseline: DiagnosisBaseline?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): DiffSynthesisOutcome {
        val primary = report.primaryFinding
            ?: return DiffSynthesisOutcome.NoActionNeeded("The diagnosis produced no findings.")

        if (primary.lever == CandidateLever.NONE) {
            return DiffSynthesisOutcome.NoActionNeeded(
                "Diagnosis '${primary.kind.title}' does not justify a bounded change.",
            )
        }

        val (patch, hypothesis, expectedEffect, risk) = when (primary.lever) {
            CandidateLever.REASONING_TOKEN_BUDGET -> tokenBudgetPatch(run, baseline)
            CandidateLever.ACTION_PRUNING -> actionPruningPatch(run, baseline)
            CandidateLever.EXPLORATION_POLICY -> explorationPatch(run)
            CandidateLever.WALL_CLOCK_BUDGET -> wallClockPatch(run, baseline)
            CandidateLever.REVERT_TO_CHAMPION -> revertPatch(baseline)
            CandidateLever.NONE -> return DiffSynthesisOutcome.NoActionNeeded("No bounded change is justified.")
        }

        val forbidden = policy.forbiddenAttempts(patch, requestedEvaluatorId = null)
        if (forbidden.isNotEmpty()) {
            return DiffSynthesisOutcome.Rejected("protected_area", "Diff touched a protected policy area.", forbidden)
        }
        registry.rejectsEvaluatorMutation(patch[BoundedPatchPolicy.KEY_EVALUATOR_ID])?.let { reason ->
            return DiffSynthesisOutcome.Rejected("evaluator_mutation", reason)
        }

        val validation = policy.validate(patch)
        if (!validation.isValid) {
            return DiffSynthesisOutcome.Rejected(
                reason = "policy_violation",
                detail = "Synthesized patch failed the bounded patch policy.",
                violations = validation.violations,
            )
        }

        return DiffSynthesisOutcome.Proposed(
            BoundedCandidateDiff(
                diffId = diffIdFactory(),
                competitionSlug = run.competitionSlug,
                baseVersionRef = baseline?.versionRef ?: run.versionRef,
                diagnosisReportId = report.reportId,
                lever = primary.lever,
                hypothesis = hypothesis,
                expectedEffect = expectedEffect,
                patch = patch,
                risk = risk,
                addressesFindings = report.findings.map { it.kind }.distinct(),
                plannedMaxTokensPerAction = plannedPace(patch, run),
                createdAtEpochMs = nowEpochMs,
            ),
        )
    }

    /**
     * Re-validates a diff that arrived from outside the app (a Colab/Kaggle receipt or a hand-edited
     * bundle) before it is allowed anywhere near the dispatch path.
     */
    fun validateImportedDiff(diff: BoundedCandidateDiff): DiffSynthesisOutcome {
        if (diff.contractVersion != BoundedCandidateDiff.CONTRACT_VERSION) {
            return DiffSynthesisOutcome.Rejected(
                "unsupported_contract",
                "Diff contract '${diff.contractVersion}' is not supported.",
            )
        }
        registry.rejectsEvaluatorMutation(diff.patch[BoundedPatchPolicy.KEY_EVALUATOR_ID])?.let {
            return DiffSynthesisOutcome.Rejected("evaluator_mutation", it)
        }
        val forbidden = policy.forbiddenAttempts(diff.patch, requestedEvaluatorId = null)
        if (forbidden.isNotEmpty()) {
            return DiffSynthesisOutcome.Rejected("protected_area", "Imported diff touched a protected area.", forbidden)
        }
        val validation = policy.validate(diff.patch)
        if (!validation.isValid) {
            return DiffSynthesisOutcome.Rejected("policy_violation", "Imported diff is out of bounds.", validation.violations)
        }
        return DiffSynthesisOutcome.Proposed(diff)
    }

    // ---- deterministic patch builders -------------------------------------------------------

    private data class PatchPlan(
        val patch: Map<String, String>,
        val hypothesis: String,
        val expectedEffect: String,
        val risk: CandidateRiskClass,
    )

    private fun tokenBudgetPatch(run: RunTelemetry, baseline: DiagnosisBaseline?): PatchPlan {
        val championPace = baseline?.tokensPerAction ?: run.tokensPerAction ?: 0.0
        // Target the champion's pace with a 10% safety margin, never below 256 tokens per action.
        val target = max(256.0, (championPace * 0.90).roundToLong().toDouble())
        return PatchPlan(
            patch = mapOf(BoundedPatchPolicy.KEY_REASONING_TOKEN_BUDGET to target.roundToLong().toString()),
            hypothesis = "Capping reasoning tokens at ${target.roundToLong()} per action removes the " +
                "measured burn (${formatMetric(run.tokensPerAction ?: 0.0)} tokens/action) without cutting " +
                "below the champion's working pace.",
            expectedEffect = "Token pace should fall toward ${formatMetric(target)} tokens/action; " +
                "score must stay within the champion's non-inferiority margin.",
            risk = CandidateRiskClass.LOW,
        )
    }

    private fun actionPruningPatch(run: RunTelemetry, baseline: DiagnosisBaseline?): PatchPlan {
        val championActionsPerLevel = baseline?.actionsPerLevel ?: run.actionsPerLevel ?: 1.0
        val target = max(1.0, (championActionsPerLevel * 0.9).roundToLong().toDouble())
        return PatchPlan(
            patch = mapOf(BoundedPatchPolicy.KEY_DEAD_ACTION_PRUNE_THRESHOLD to target.roundToLong().toString()),
            hypothesis = "Pruning actions that produce no state change after $target repeats should " +
                "remove wasted actions per level.",
            expectedEffect = "Actions per level should drop toward ${formatMetric(target)} with an unchanged score.",
            risk = CandidateRiskClass.MEDIUM,
        )
    }

    private fun explorationPatch(run: RunTelemetry): PatchPlan {
        val current = run.diagnostics[BoundedPatchPolicy.KEY_EXPLORATION_TEMPERATURE]?.toDoubleOrNull() ?: 0.7
        val next = (current - 0.2).coerceIn(0.0, 1.0)
        return PatchPlan(
            patch = mapOf(BoundedPatchPolicy.KEY_EXPLORATION_TEMPERATURE to formatDecimal(next)),
            hypothesis = "Reducing exploration temperature from ${formatDecimal(current)} to " +
                "${formatDecimal(next)} should spend fewer tokens on low-value frontier branches.",
            expectedEffect = "Lower token pace with equal or better level completion.",
            risk = CandidateRiskClass.MEDIUM,
        )
    }

    private fun wallClockPatch(run: RunTelemetry, baseline: DiagnosisBaseline?): PatchPlan {
        val championWallClock = baseline?.wallClockMs?.takeIf { it > 0 } ?: max(run.wallClockMs, 60_000L)
        val target = max(60_000L, (championWallClock * 1.1).toLong())
        return PatchPlan(
            patch = mapOf(BoundedPatchPolicy.KEY_MAX_WALL_CLOCK_MS to target.toString()),
            hypothesis = "Bounding wall clock at ${target} ms stops the run drifting past the champion's duration.",
            expectedEffect = "Comparable wall clock to the champion with no score loss.",
            risk = CandidateRiskClass.LOW,
        )
    }

    private fun revertPatch(baseline: DiagnosisBaseline?): PatchPlan {
        val versionRef = baseline?.versionRef ?: "champion"
        return PatchPlan(
            patch = mapOf(BoundedPatchPolicy.KEY_REVERT_TO_VERSION to versionRef),
            hypothesis = "The run regressed, so restoring champion configuration $versionRef is the " +
                "only change with evidence behind it.",
            expectedEffect = "Score returns to the locked champion value; no new capability is claimed.",
            risk = CandidateRiskClass.HIGH,
        )
    }

    private fun plannedPace(patch: Map<String, String>, run: RunTelemetry): Double {
        patch[BoundedPatchPolicy.KEY_REASONING_TOKEN_BUDGET]?.toDoubleOrNull()?.let { return it }
        return run.tokensPerAction ?: 0.0
    }

    private fun formatDecimal(value: Double): String = String.format(Locale.US, "%.2f", value)
}
