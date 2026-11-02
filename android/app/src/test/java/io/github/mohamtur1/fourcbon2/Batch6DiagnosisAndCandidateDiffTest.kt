package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Division 45 + 46 verification: diagnosis rules, and the bounded candidate diff policy.
 *
 * Fixtures are the live campaign evidence: the champion at 25.83, the v4 regression to 25.15, and the
 * `r11l` token burn (96,609 tokens for 53 actions).
 */
class Batch6DiagnosisAndCandidateDiffTest {
    private val championBaseline = DiagnosisBaseline(
        versionRef = "v3",
        metricName = "ARC-AGI-3 score",
        primaryMetric = 25.83,
        higherIsBetter = true,
        tokensPerAction = 400.0,
        actionsPerLevel = 100.0,
        levelsCompleted = 5,
        totalActions = 500,
        wallClockMs = 600_000L,
    )

    private fun telemetry(
        score: Double,
        levels: Int = 4,
        actions: Int = 500,
        tokens: Long = 200_000L,
        wallClockMs: Long = 600_000L,
        versionRef: String = "v4",
    ): RunTelemetry = RunTelemetry(
        telemetryId = "tel_${versionRef}_$actions",
        competitionSlug = "arc-prize-2026",
        benchmarkKind = BenchmarkKind.ARC_AGI_INTERACTIVE,
        runLabel = "campaign run $versionRef",
        versionRef = versionRef,
        metricName = "ARC-AGI-3 score",
        primaryMetric = score,
        higherIsBetter = true,
        levelsCompleted = levels,
        totalActions = actions,
        tokensUsed = tokens,
        wallClockMs = wallClockMs,
        source = TelemetrySourceKind.DIAGNOSTICS_JSON,
        completedAtEpochMs = 1_000L,
        recordedAtEpochMs = 1_000L,
    )

    @Test
    fun diagnoserFindsTheV4RegressionAndPointsAtRevertWithEvidence() {
        val candidate = telemetry(score = 25.15, levels = 4)
        val report = RunDiagnoser().diagnose(candidate, championBaseline, nowEpochMs = 5_000L)

        assertEquals("tel_v4_500", report.telemetryId)
        assertEquals("v3", report.baselineVersionRef)
        assertEquals(FindingKind.SCORE_REGRESSION, report.primaryFinding!!.kind)
        assertEquals(FindingSeverity.HIGH, report.primaryFinding!!.severity)
        assertEquals(CandidateLever.REVERT_TO_CHAMPION, report.recommendedLever)

        // The regression is quantified, not asserted vaguely.
        assertEquals(25.15 - 25.83, report.scoreDelta!!, 1e-9)
        val evidence = report.primaryFinding!!.evidence.joinToString(" ")
        assertTrue(evidence.contains("25.15"))
        assertTrue(evidence.contains("25.83"))

        // Durable + round-trippable report.
        val roundTripped = DiagnosisReport.fromJson(report.toJson())
        assertEquals(report.reportId, roundTripped.reportId)
        assertEquals(1, roundTripped.findings.size)
        assertEquals(CandidateLever.REVERT_TO_CHAMPION, roundTripped.recommendedLever)
    }

    @Test
    fun diagnoserEscalatesDeepRegressionsAndFlagsTokenBurnAndWallClockInflation() {
        val burnt = telemetry(score = 24.0, levels = 4, actions = 53, tokens = 96_609L, wallClockMs = 1_800_000L)
        val report = RunDiagnoser().diagnose(burnt, championBaseline, nowEpochMs = 6_000L)

        val kinds = report.findings.map { it.kind }
        assertTrue(kinds.contains(FindingKind.SCORE_REGRESSION))
        assertTrue(kinds.contains(FindingKind.TOKEN_BURN))
        assertTrue(kinds.contains(FindingKind.WALL_CLOCK_INFLATION))
        assertEquals(FindingSeverity.CRITICAL, report.primaryFinding!!.severity)
        assertTrue(report.requiresOwnerVisibleAlert)

        val tokenFinding = report.findings.first { it.kind == FindingKind.TOKEN_BURN }
        assertEquals(CandidateLever.REASONING_TOKEN_BUDGET, tokenFinding.lever)
        assertTrue(tokenFinding.evidence.joinToString(" ").contains("96609"))

        // Token pace delta is exposed for the UI: (1822.8 / 400 - 1) * 100.
        assertEquals((96_609.0 / 53.0 / 400.0 - 1.0) * 100.0, report.tokenPaceDeltaPercent!!, 1e-9)

        // Primary finding is the most severe one (score), and ordering is deterministic.
        assertEquals(FindingKind.SCORE_REGRESSION, report.primaryFinding!!.kind)
        val rerun = RunDiagnoser().diagnose(burnt, championBaseline, nowEpochMs = 6_000L)
        assertEquals(report.findings.map { it.kind }, rerun.findings.map { it.kind })
    }

    @Test
    fun diagnoserRefusesToCallNoiseARegressionAndSaysSoWhenUnusable() {
        // 0.4% drop is inside the 2% noise tolerance → inconclusive, no lever.
        val noise = RunDiagnoser().diagnose(telemetry(score = 25.73), championBaseline, nowEpochMs = 7_000L)
        assertEquals(FindingKind.INSUFFICIENT_EVIDENCE, noise.primaryFinding!!.kind)
        assertEquals(CandidateLever.NONE, noise.recommendedLever)
        assertFalse(noise.requiresOwnerVisibleAlert)

        // A three-action run cannot support any claim.
        val tiny = RunDiagnoser().diagnose(
            telemetry(score = 90.0, actions = 3, tokens = 500L), championBaseline, nowEpochMs = 7_500L,
        )
        assertEquals(FindingKind.TELEMETRY_UNUSABLE, tiny.primaryFinding!!.kind)
        assertEquals(CandidateLever.NONE, tiny.recommendedLever)

        // No champion yet → informational, no change proposed.
        val noChampion = RunDiagnoser().diagnose(telemetry(score = 25.15), null, nowEpochMs = 8_000L)
        assertEquals(FindingKind.CHAMPION_ABSENT, noChampion.primaryFinding!!.kind)
    }

    @Test
    fun synthesizerProducesExactlyOneBoundedChangeOrHonestlyNothing() {
        val synthesizer = CandidateDiffSynthesizer(diffIdFactory = { "diff_test" })

        val burnReport = RunDiagnoser(reportIdFactory = { "diag_burn" }).diagnose(
            telemetry(score = 25.83, actions = 53, tokens = 40_000L), championBaseline, nowEpochMs = 9_000L,
        )
        assertEquals(FindingKind.TOKEN_BURN, burnReport.primaryFinding!!.kind)

        val proposed = synthesizer.synthesize(
            burnReport, telemetry(score = 25.83, actions = 53, tokens = 40_000L), championBaseline, nowEpochMs = 9_100L,
        )
        val diff = (proposed as DiffSynthesisOutcome.Proposed).diff
        assertEquals(CandidateLever.REASONING_TOKEN_BUDGET, diff.lever)
        assertEquals(1, diff.patch.size)
        assertEquals("360", diff.patch[BoundedPatchPolicy.KEY_REASONING_TOKEN_BUDGET]) // champion pace × 0.90
        assertEquals(CandidateRiskClass.LOW, diff.risk)
        assertTrue(diff.hypothesis.contains("360"))
        assertTrue(diff.addressesFindings.contains(FindingKind.TOKEN_BURN))
        assertEquals(BoundedPatchPolicy.KEY_REASONING_TOKEN_BUDGET, diff.patch.keys.single())

        // Stable run → no busywork.
        val stableReport = RunDiagnoser(reportIdFactory = { "diag_stable" }).diagnose(
            telemetry(score = 25.83), championBaseline, nowEpochMs = 9_200L,
        )
        val nothing = synthesizer.synthesize(stableReport, telemetry(score = 25.83), championBaseline, 9_300L)
        assertTrue(nothing is DiffSynthesisOutcome.NoActionNeeded)

        // Regression → single revert lever, marked high risk.
        val regressionReport = RunDiagnoser(reportIdFactory = { "diag_reg" }).diagnose(
            telemetry(score = 25.15), championBaseline, nowEpochMs = 9_400L,
        )
        val revert = synthesizer.synthesize(regressionReport, telemetry(score = 25.15), championBaseline, 9_500L)
        val revertDiff = (revert as DiffSynthesisOutcome.Proposed).diff
        assertEquals(CandidateLever.REVERT_TO_CHAMPION, revertDiff.lever)
        assertEquals("v3", revertDiff.patch[BoundedPatchPolicy.KEY_REVERT_TO_VERSION])
        assertEquals(CandidateRiskClass.HIGH, revertDiff.risk)
    }

    @Test
    fun patchPolicyRejectsMultiKeyProtectedAndOutOfBoundsChanges() {
        val policy = BoundedPatchPolicy(ExperimentBudgetCaps.OWNER_APPROVED_DEFAULT)

        assertTrue(policy.validate(mapOf(BoundedPatchPolicy.KEY_REASONING_TOKEN_BUDGET to "3000")).isValid)
        assertTrue(policy.validate(mapOf(BoundedPatchPolicy.KEY_EXPLORATION_TEMPERATURE to "0.5")).isValid)

        // Two changes cannot be attributed to one cause.
        val multiKey = policy.validate(
            mapOf(
                BoundedPatchPolicy.KEY_REASONING_TOKEN_BUDGET to "3000",
                BoundedPatchPolicy.KEY_EXPLORATION_TEMPERATURE to "0.5",
            ),
        )
        assertFalse(multiKey.isValid)
        assertTrue(multiKey.violations.single().contains("at most 1"))

        // Unknown keys are unreachable.
        assertFalse(policy.validate(mapOf("evaluator_seed" to "1")).isValid)
        assertFalse(policy.validate(mapOf("submission_daily_cap" to "999")).isValid)

        // Values outside the declared range are refused.
        assertFalse(policy.validate(mapOf(BoundedPatchPolicy.KEY_REASONING_TOKEN_BUDGET to "999999")).isValid)
        assertFalse(policy.validate(mapOf(BoundedPatchPolicy.KEY_EXPLORATION_TEMPERATURE to "2.0")).isValid)
        assertFalse(policy.validate(emptyMap()).isValid)

        // Protected areas are named explicitly before value checks.
        assertTrue(policy.forbiddenAttempts(mapOf("submission_daily_cap" to "999"), null).isNotEmpty())
        assertTrue(policy.forbiddenAttempts(mapOf("credential_slot" to "kaggle"), null).isNotEmpty())
        assertTrue(policy.forbiddenAttempts(mapOf(BoundedPatchPolicy.KEY_REASONING_TOKEN_BUDGET to "3000"), null).isEmpty())
    }

    @Test
    fun synthesizerCannotGrantItselfABudgetItWouldNeedAnOwnerToApprove() {
        // Champion pace is far above the owner-approved per-action cap.
        val expensiveChampion = championBaseline.copy(tokensPerAction = 12_000.0)
        val run = telemetry(score = 25.83, actions = 53, tokens = 900_000L)
        val report = RunDiagnoser(reportIdFactory = { "diag_expensive" }).diagnose(run, expensiveChampion, 10_000L)
        assertEquals(FindingKind.TOKEN_BURN, report.primaryFinding!!.kind)

        val outcome = CandidateDiffSynthesizer(
            policy = BoundedPatchPolicy(ExperimentBudgetCaps.OWNER_APPROVED_DEFAULT),
            diffIdFactory = { "diff_over_cap" },
        ).synthesize(report, run, expensiveChampion, 10_100L)

        val rejected = outcome as DiffSynthesisOutcome.Rejected
        assertEquals("policy_violation", rejected.reason)
        assertTrue(rejected.violations.joinToString(" ").contains("outside the allowed range"))
    }

    @Test
    fun importedDiffsAreRevalidatedBeforeTheyCanReachADispatch() {
        val synthesizer = CandidateDiffSynthesizer(diffIdFactory = { "diff_import" })
        val base = BoundedCandidateDiff(
            diffId = "diff_evil",
            competitionSlug = "arc-prize-2026",
            baseVersionRef = "v3",
            diagnosisReportId = "diag_x",
            lever = CandidateLever.REASONING_TOKEN_BUDGET,
            hypothesis = "imported",
            expectedEffect = "imported",
            patch = mapOf("evaluator_id" to "eval.candidate_side_grading.v1"),
            risk = CandidateRiskClass.LOW,
            addressesFindings = emptyList(),
            plannedMaxTokensPerAction = 400.0,
            createdAtEpochMs = 11_000L,
        )
        // Naming a replacement evaluator is refused with a precise reason.
        val rejected = synthesizer.validateImportedDiff(base) as DiffSynthesisOutcome.Rejected
        assertEquals("evaluator_mutation", rejected.reason)
        assertTrue(rejected.detail.contains("owner-installed"))

        // Reaching for a protected policy area is refused before any value check.
        val protectedArea = synthesizer.validateImportedDiff(
            base.copy(patch = mapOf("submission_daily_cap" to "999")),
        ) as DiffSynthesisOutcome.Rejected
        assertEquals("protected_area", protectedArea.reason)
        assertTrue(protectedArea.violations.isNotEmpty())

        val unsupportedContract = synthesizer.validateImportedDiff(base.copy(contractVersion = "4cbon2.candidate_diff.v0"))
        assertEquals("unsupported_contract", (unsupportedContract as DiffSynthesisOutcome.Rejected).reason)

        val benign = base.copy(
            patch = mapOf(BoundedPatchPolicy.KEY_REASONING_TOKEN_BUDGET to "512"),
            contractVersion = BoundedCandidateDiff.CONTRACT_VERSION,
        )
        assertNotNull((synthesizer.validateImportedDiff(benign) as DiffSynthesisOutcome.Proposed).diff)
        assertNull((synthesizer.validateImportedDiff(base.copy(patch = emptyMap())) as? DiffSynthesisOutcome.Proposed))
    }
}
