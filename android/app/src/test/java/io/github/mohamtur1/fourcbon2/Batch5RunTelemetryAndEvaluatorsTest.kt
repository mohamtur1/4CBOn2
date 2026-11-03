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
 * Division 43 + 44 verification: the v0.3 run telemetry contract and the fixed evaluator registry.
 *
 * The fixtures are the real numbers from the v0.2 → v0.3 handoff: ARC-AGI-3 game `r11l` burned 96,609
 * tokens across 53 actions, and version `v4` regressed the leaderboard from 25.83 to 25.15.
 */
class Batch5RunTelemetryAndEvaluatorsTest {
    private fun newDatabase(prefix: String): FourCBOn2Database {
        val dir: File = Files.createTempDirectory(prefix).toFile()
        return FourCBOn2Database(DiskDirectoryBackend(dir))
    }

    private val r11lDiagnostics = """
        {"game":"r11l","score":0.10,"levels":3,"actions":53,"tokens":96609,
         "elapsed_ms":1800000,"version":"364758641","status":"complete"}
    """.trimIndent()

    @Test
    fun arcDiagnosticsIngestNormalizesRunAndDerivesTokenPace() {
        val database = newDatabase("4cbon2_div43_ingest")
        val ingest = RunTelemetryIngest(database) { "tel_r11l" }

        val outcome = ingest.ingestDiagnosticsJson(
            rawJson = r11lDiagnostics,
            competitionSlug = "arc-prize-2026",
            runLabel = "r11l frontier run",
            sourceRef = "kernel:364758641",
            nowEpochMs = 1_000L,
        )

        val telemetry = (outcome as TelemetryIngestOutcome.Ingested).telemetry
        assertEquals(RunTelemetry.CONTRACT_VERSION, telemetry.contractVersion)
        assertEquals(BenchmarkKind.ARC_AGI_INTERACTIVE, telemetry.benchmarkKind)
        assertEquals(0.10, telemetry.primaryMetric, 1e-12)
        assertEquals(3, telemetry.levelsCompleted)
        assertEquals(53, telemetry.totalActions)
        assertEquals(96_609L, telemetry.tokensUsed)
        assertEquals(1_800_000L, telemetry.wallClockMs)
        assertEquals("364758641", telemetry.versionRef)
        assertTrue(telemetry.higherIsBetter)

        // Token-pace invariants: the exact numbers that motivated v0.3.
        val pace = telemetry.tokensPerAction
        assertNotNull(pace)
        assertEquals(96_609.0 / 53.0, pace!!, 1e-9)
        assertTrue(pace!! > 1_800.0)
        assertEquals(53.0 / 3.0, telemetry.actionsPerLevel!!, 1e-9)

        // Round-trip through durable storage, including the diagnostics map.
        val reloaded = ingest.get("tel_r11l")
        assertNotNull(reloaded)
        assertEquals(96_609L, reloaded!!.tokensUsed)
        assertEquals("r11l", reloaded.diagnostics["game"])
        assertEquals(listOf("tel_r11l"), ingest.listFor("arc-prize-2026").map { it.telemetryId })
    }

    @Test
    fun ingestRejectsIncompleteOrMalformedEvidenceInsteadOfInventingValues() {
        val database = newDatabase("4cbon2_div43_reject")
        val ingest = RunTelemetryIngest(database) { "tel_test" }

        val missingTokens = ingest.ingestDiagnosticsJson(
            """{"score":0.1,"levels":2,"actions":30}""", "arc-prize-2026", "no tokens",
        )
        assertEquals("missing_tokens", (missingTokens as TelemetryIngestOutcome.Rejected).reason)

        val missingScore = ingest.ingestDiagnosticsJson(
            """{"actions":30,"tokens":900}""", "arc-prize-2026", "no score",
        )
        assertEquals("missing_score", (missingScore as TelemetryIngestOutcome.Rejected).reason)

        val malformed = ingest.ingestDiagnosticsJson("not json at all", "arc-prize-2026", "bad")
        assertEquals("malformed_json", (malformed as TelemetryIngestOutcome.Rejected).reason)

        // A queued kernel is not evidence that the candidate was evaluated.
        val queued = ingest.ingestKaggleKernelStatus(
            """{"status":"queued","score":0.1}""", "arc-prize-2026",
        )
        assertEquals("kernel_not_complete", (queued as TelemetryIngestOutcome.Rejected).reason)

        // Nothing at all was stored for any rejected payload.
        assertNull(ingest.get("tel_test"))
        assertTrue(ingest.listAll().isEmpty())
    }

    @Test
    fun ingestStripsCredentialShapedMaterialBeforeStoringTelemetry() {
        val database = newDatabase("4cbon2_div43_redaction")
        val ingest = RunTelemetryIngest(database) { "tel_secret" }

        val outcome = ingest.ingestDiagnosticsJson(
            rawJson = """
                {"game":"r11l","score":0.1,"levels":1,"actions":20,"tokens":5000,
                 "status":"complete",
                 "extras":{"note":"auth header was Bearer abcdef1234567890",
                           "spoken":"used KGAT_abcdefghijklmnop1234 to poll",
                           "harmless":"level 1 cleared"}}
            """.trimIndent(),
            competitionSlug = "arc-prize-2026",
            runLabel = "redaction probe",
            nowEpochMs = 50L,
        )

        val telemetry = (outcome as TelemetryIngestOutcome.Ingested).telemetry
        assertTrue(telemetry.redactionApplied)
        val stored = JsonCodec.stringify(telemetry.toJson())
        assertFalse(stored.contains("KGAT_"))
        assertFalse(stored.contains("Bearer abcdef"))
        assertEquals("level 1 cleared", telemetry.diagnostics["harmless"])

        // A credentials-shaped key is dropped entirely rather than masked.
        val sanitized = TelemetrySanitizer.sanitizeDiagnostics(
            mapOf("api_key" to "value", "kaggle_token" to "x", "score" to "0.1"),
        )
        assertTrue(sanitized.second)
        assertEquals(setOf("score"), sanitized.first.keys)
    }

    @Test
    fun fixedEvaluatorsScoreOnlyComparableEvidenceAndRefuseSelfMutation() {
        val registry = FixedEvaluatorRegistry()
        val arc = ArcAgiInteractiveEvaluator()

        val good = RunTelemetry(
            telemetryId = "tel_ok",
            competitionSlug = "arc-prize-2026",
            benchmarkKind = BenchmarkKind.ARC_AGI_INTERACTIVE,
            runLabel = "champion run",
            versionRef = "v3",
            metricName = "ARC-AGI-3 score",
            primaryMetric = 25.83,
            higherIsBetter = true,
            levelsCompleted = 4,
            totalActions = 120,
            tokensUsed = 48_000,
            wallClockMs = 600_000,
            source = TelemetrySourceKind.DIAGNOSTICS_JSON,
            completedAtEpochMs = 900L,
            recordedAtEpochMs = 900L,
        )
        val scored = (arc.evaluate(good) as EvaluationOutcome.Computed).score
        assertEquals(25.83, scored.value, 1e-12)
        assertEquals(MetricDirection.HIGHER_IS_BETTER, scored.direction)
        assertEquals(400.0, scored.tokensPerAction!!, 1e-9)

        // Too few actions → inconclusive, not a score.
        val tiny = arc.evaluate(good.copy(totalActions = 3))
        assertTrue(tiny is EvaluationOutcome.Inconclusive)
        assertEquals("too_few_actions", (tiny as EvaluationOutcome.Inconclusive).reason)

        // Zero levels → inconclusive.
        val noLevels = arc.evaluate(good.copy(levelsCompleted = 0))
        assertTrue(noLevels is EvaluationOutcome.Inconclusive)

        // Metric from the wrong family → rejected outright.
        val wrongMetric = arc.evaluate(good.copy(metricName = "CV_RMSE"))
        assertTrue(wrongMetric is EvaluationOutcome.Rejected)
        assertEquals("unknown_metric", (wrongMetric as EvaluationOutcome.Rejected).reason)

        // Unfinished run → inconclusive.
        val unfinished = arc.evaluate(good.copy(completedAtEpochMs = 0L))
        assertTrue(unfinished is EvaluationOutcome.Inconclusive)

        // Registry coverage + immutability.
        assertEquals(
            listOf("eval.arc_agi_interactive.v1", "eval.scalar_metric.v1", "eval.tabular_cv_rmse.v1"),
            registry.installedEvaluatorIds,
        )
        assertNull(registry.rejectsEvaluatorMutation("eval.arc_agi_interactive.v1"))
        assertNotNull(registry.rejectsEvaluatorMutation("eval.candidate_side_grading.v1"))
        assertEquals(
            "unknown_metric",
            (registry.evaluate(good.copy(benchmarkKind = BenchmarkKind.TABULAR_CSV_CV)) as EvaluationOutcome.Rejected).reason,
        )
        // A registry with no installed evaluator refuses to score at all.
        assertEquals(
            "no_evaluator",
            (FixedEvaluatorRegistry(emptyList()).evaluate(good) as EvaluationOutcome.Rejected).reason,
        )
    }

    @Test
    fun tabularEvaluatorKeepsLowerIsBetterDirectionAndPrefersExportedDiagnostics() {
        val evaluator = TabularCvRmseEvaluator()
        val run = RunTelemetry(
            telemetryId = "tel_tab",
            competitionSlug = "titanic",
            benchmarkKind = BenchmarkKind.TABULAR_CSV_CV,
            runLabel = "baseline",
            versionRef = "ver_base",
            metricName = "CV_RMSE",
            primaryMetric = 0.9,
            higherIsBetter = false,
            levelsCompleted = 1,
            totalActions = 1,
            tokensUsed = 0,
            wallClockMs = 1000,
            source = TelemetrySourceKind.MANUAL_ENTRY,
            completedAtEpochMs = 10L,
            recordedAtEpochMs = 10L,
            diagnostics = mapOf("mean_cv_rmse" to "0.4213"),
        )
        val score = (evaluator.evaluate(run) as EvaluationOutcome.Computed).score
        assertEquals(0.4213, score.value, 1e-12)
        assertEquals(MetricDirection.LOWER_IS_BETTER, score.direction)
    }
}
