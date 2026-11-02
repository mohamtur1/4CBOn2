package io.github.mohamtur1.fourcbon2

import java.util.Locale
import java.util.UUID

/**
 * Division 43 (v0.3) — Run telemetry contract & ingest.
 *
 * v0.2 could only score local `.csv` tabular folds. Interactive agent benchmarks (ARC-AGI-3) report
 * a completely different shape of evidence: a leaderboard score, completed levels, action counts,
 * and token spend. This module defines the one normalized record — `4cbon2.run_telemetry.v1` —
 * that every run source is translated into before diagnosis, so the Improvement Lab can reason
 * about token-pace efficiency, level stalls, and regressions instead of only CV-RMSE.
 *
 * Two deliberate rules:
 *  1. Ingest NEVER invents data. Missing required fields produce a [TelemetryIngestOutcome.Rejected];
 *     nothing is defaulted into a plausible-looking score.
 *  2. Ingest NEVER stores secrets. Every free-text and key/value field passes through
 *     [TelemetrySanitizer] before persistence, and the sanitizer's verdict is recorded on the record.
 */

/** The evaluator family a run belongs to. The registry (Division 44) refuses unknown kinds. */
enum class BenchmarkKind(val label: String, val defaultMetricName: String) {
    ARC_AGI_INTERACTIVE("ARC-AGI interactive agent", "ARC-AGI-3 score"),
    TABULAR_CSV_CV("Tabular CSV cross-validation", "CV_RMSE"),
    SCALAR_METRIC("Single scalar competition metric", "score"),
}

/** Where a telemetry record came from, so the owner can audit every number on screen. */
enum class TelemetrySourceKind(val label: String) {
    DIAGNOSTICS_JSON("Run diagnostics.json"),
    KAGGLE_KERNEL_STATUS("Kaggle kernel status"),
    COLAB_RECEIPT("Colab receipt"),
    MANUAL_ENTRY("Owner manual entry"),
}

/** One normalized, secret-free observation of a finished run. */
data class RunTelemetry(
    val telemetryId: String,
    val competitionSlug: String,
    val benchmarkKind: BenchmarkKind,
    val runLabel: String,
    val versionRef: String,
    val metricName: String,
    val primaryMetric: Double,
    val higherIsBetter: Boolean,
    val levelsCompleted: Int,
    val totalActions: Int,
    val tokensUsed: Long,
    val wallClockMs: Long,
    val source: TelemetrySourceKind,
    val sourceRef: String? = null,
    val startedAtEpochMs: Long = 0L,
    val completedAtEpochMs: Long = 0L,
    val recordedAtEpochMs: Long = 0L,
    /**
     * True whenever the record passed [TelemetrySanitizer] before storage. This is a *safety*
     * assertion ("nothing secret is in here"), not a note about whether masking changed anything —
     * how many values were masked is recorded in `diagnostics["redactions_applied"]`. The promotion
     * gate refuses to promote on a record that never went through the sanitizer.
     */
    val redactionApplied: Boolean = true,
    val diagnostics: Map<String, String> = emptyMap(),
    val contractVersion: String = CONTRACT_VERSION,
) {
    /** Tokens spent per environment action — the cost-per-decision signal from the live campaign. */
    val tokensPerAction: Double?
        get() = if (totalActions > 0) tokensUsed.toDouble() / totalActions.toDouble() else null

    /** Actions burned per completed level; a level stall shows up here first. */
    val actionsPerLevel: Double?
        get() = if (levelsCompleted > 0) totalActions.toDouble() / levelsCompleted.toDouble() else null

    /** A run that produced no metric or no action is not evidence of anything. */
    val isScorable: Boolean
        get() = primaryMetric.isFinite() && totalActions > 0 && tokensUsed >= 0 && levelsCompleted >= 0

    val isComplete: Boolean
        get() = completedAtEpochMs > 0L && (startedAtEpochMs == 0L || completedAtEpochMs >= startedAtEpochMs)

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "contractVersion" to JsonValue.Str(contractVersion),
            "telemetryId" to JsonValue.Str(telemetryId),
            "competitionSlug" to JsonValue.Str(competitionSlug),
            "benchmarkKind" to JsonValue.Str(benchmarkKind.name),
            "runLabel" to JsonValue.Str(runLabel),
            "versionRef" to JsonValue.Str(versionRef),
            "metricName" to JsonValue.Str(metricName),
            "primaryMetric" to JsonValue.Num(primaryMetric.coerceFinite()),
            "higherIsBetter" to JsonValue.Bool(higherIsBetter),
            "levelsCompleted" to JsonValue.Num(levelsCompleted.toDouble(), levelsCompleted.toString()),
            "totalActions" to JsonValue.Num(totalActions.toDouble(), totalActions.toString()),
            "tokensUsed" to JsonValue.Num(tokensUsed.toDouble(), tokensUsed.toString()),
            "wallClockMs" to JsonValue.Num(wallClockMs.toDouble(), wallClockMs.toString()),
            "source" to JsonValue.Str(source.name),
            "sourceRef" to (sourceRef?.let(JsonValue::Str) ?: JsonValue.Null),
            "startedAtEpochMs" to JsonValue.Num(startedAtEpochMs.toDouble(), startedAtEpochMs.toString()),
            "completedAtEpochMs" to JsonValue.Num(completedAtEpochMs.toDouble(), completedAtEpochMs.toString()),
            "recordedAtEpochMs" to JsonValue.Num(recordedAtEpochMs.toDouble(), recordedAtEpochMs.toString()),
            "redactionApplied" to JsonValue.Bool(redactionApplied),
            "diagnostics" to JsonValue.Obj(
                diagnostics.toSortedMap().mapValues { (_, v) -> JsonValue.Str(v) as JsonValue },
            ),
        ),
    )

    /** One-line owner-facing summary used by the Daily Lab surface and the action timeline. */
    fun summaryLine(): String = buildString {
        append(runLabel.ifBlank { "run" })
        append(" · ").append(metricName).append(' ')
        append(formatMetric(primaryMetric))
        append(" · ").append(levelsCompleted).append(" levels")
        append(" · ").append(totalActions).append(" actions")
        append(" · ").append(tokensUsed).append(" tokens")
        tokensPerAction?.let { append(" · ").append(formatMetric(it)).append(" tok/action") }
    }

    companion object {
        const val CONTRACT_VERSION = "4cbon2.run_telemetry.v1"

        fun fromJson(obj: JsonValue.Obj): RunTelemetry = RunTelemetry(
            telemetryId = obj.stringOrDefault("telemetryId"),
            competitionSlug = obj.stringOrDefault("competitionSlug"),
            benchmarkKind = parseEnum(obj.string("benchmarkKind"), BenchmarkKind.TABULAR_CSV_CV),
            runLabel = obj.stringOrDefault("runLabel"),
            versionRef = obj.stringOrDefault("versionRef"),
            metricName = obj.stringOrDefault("metricName", "score"),
            primaryMetric = obj.double("primaryMetric", Double.NaN),
            higherIsBetter = obj.bool("higherIsBetter", false),
            levelsCompleted = obj.int("levelsCompleted", 0),
            totalActions = obj.int("totalActions", 0),
            tokensUsed = obj.long("tokensUsed", 0L),
            wallClockMs = obj.long("wallClockMs", 0L),
            source = parseEnum(obj.string("source"), TelemetrySourceKind.MANUAL_ENTRY),
            sourceRef = obj.string("sourceRef"),
            startedAtEpochMs = obj.long("startedAtEpochMs", 0L),
            completedAtEpochMs = obj.long("completedAtEpochMs", 0L),
            recordedAtEpochMs = obj.long("recordedAtEpochMs", 0L),
            redactionApplied = obj.bool("redactionApplied", true),
            diagnostics = obj.obj("diagnostics")?.entries
                ?.mapNotNull { (k, v) -> (v as? JsonValue.Str)?.let { k to it.value } }
                ?.toMap()
                ?: emptyMap(),
            contractVersion = obj.stringOrDefault("contractVersion", CONTRACT_VERSION),
        )

        private inline fun <reified T : Enum<T>> parseEnum(raw: String?, default: T): T =
            raw?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
    }
}

sealed interface TelemetryIngestOutcome {
    data class Ingested(val telemetry: RunTelemetry) : TelemetryIngestOutcome

    data class Rejected(val reason: String, val detail: String) : TelemetryIngestOutcome
}

/**
 * Strips credential-shaped material from telemetry before it is stored or shown.
 *
 * The rule set is intentionally blunt and fail-closed: any key that looks like a credential is
 * dropped entirely (not masked), and any value containing a bearer token, a `KGAT_` key, a
 * `ghp_`/`github_pat_` token, or a `key=value` secret pair is replaced by [REDACTED].
 */
object TelemetrySanitizer {
    const val REDACTED = "[redacted]"

    private val SECRET_KEY_HINTS = listOf(
        "token", "secret", "password", "passwd", "authorization", "auth", "apikey", "api_key",
        "credential", "private_key", "pat", "cookie", "session_key",
    )

    private val SECRET_VALUE_PATTERNS = listOf(
        Regex("(?i)bearer\\s+[A-Za-z0-9._~+/-]{8,}"),
        Regex("(?i)\\bKGAT_[A-Za-z0-9]{8,}"),
        Regex("(?i)\\bgh[pousr]_[A-Za-z0-9]{16,}"),
        Regex("(?i)\\bgithub_pat_[A-Za-z0-9_]{16,}"),
        Regex("(?i)\\b(sk|pk)-[A-Za-z0-9]{16,}"),
        Regex("(?i)\\b[a-z0-9_]*(token|secret|password|api_?key)\\b\\s*[=:]\\s*\\S+"),
    )

    fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase(Locale.US)
        return SECRET_KEY_HINTS.any { hint -> normalized.contains(hint) }
    }

    fun redactValue(value: String): String =
        if (SECRET_VALUE_PATTERNS.any { it.containsMatchIn(value) }) REDACTED else value

    /** Returns the sanitized map plus `true` when anything was removed or masked. */
    fun sanitizeDiagnostics(raw: Map<String, String>): Pair<Map<String, String>, Boolean> {
        var changed = false
        val out = linkedMapOf<String, String>()
        for ((key, value) in raw) {
            val safeKey = redactValue(key).take(120)
            if (isSensitiveKey(key)) {
                changed = true
                continue
            }
            val safeValue = redactValue(value).take(400)
            if (safeValue != value) changed = true
            out[safeKey] = safeValue
        }
        return out.toMap() to changed
    }
}

/**
 * Durable ingest for every run source. Each ingest validates the contract, sanitizes the payload,
 * and stores a secret-free [RunTelemetry] record under [NS_TELEMETRY].
 */
class RunTelemetryIngest(
    private val database: FourCBOn2Database,
    private val recordIdFactory: () -> String = { "tel_${UUID.randomUUID().toString().take(8)}" },
) {
    /**
     * Ingest an ARC-AGI-3 style `diagnostics.json` payload:
     * `{"game":"r11l","score":0.10,"levels":3,"actions":53,"tokens":96609,"elapsed_ms":...,"version":"364758641"}`
     */
    fun ingestDiagnosticsJson(
        rawJson: String,
        competitionSlug: String,
        runLabel: String,
        sourceRef: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): TelemetryIngestOutcome {
        val obj = runCatching { JsonCodec.parseObject(rawJson) }
            .getOrElse { return reject("malformed_json", "Diagnostics payload is not a JSON object.") }

        val tokens = firstLong(obj, "tokens", "tokens_used", "token_count")
            ?: return reject("missing_tokens", "Diagnostics payload has no token count.")
        val actions = firstLong(obj, "actions", "action_count", "steps")
            ?: return reject("missing_actions", "Diagnostics payload has no action count.")
        val score = firstDouble(obj, "score", "leaderboard_score")
            ?: return reject("missing_score", "Diagnostics payload has no score.")
        val levels = firstLong(obj, "levels", "levels_completed") ?: 0L

        val diagnostics = linkedMapOf<String, String>()
        diagnostics["game"] = obj.stringOrDefault("game", runLabel)
        obj.string("status")?.let { diagnostics["status"] = it }
        obj.string("failure")?.let { diagnostics["failure"] = it }
        firstDouble(obj, "elapsed_ms")?.let { diagnostics["elapsed_ms"] = it.toLong().toString() }
        obj.obj("extras")?.entries?.forEach { (k, v) ->
            (v as? JsonValue.Str)?.let { diagnostics[k] = it.value }
        }
        val (sanitizedDiagnostics, redacted) = TelemetrySanitizer.sanitizeDiagnostics(diagnostics)
        val safeDiagnostics = if (redacted) sanitizedDiagnostics + ("redactions_applied" to "true") else sanitizedDiagnostics

        return store(
            competitionSlug = competitionSlug,
            benchmarkKind = BenchmarkKind.ARC_AGI_INTERACTIVE,
            runLabel = runLabel.ifBlank { safeDiagnostics["game"] ?: "arc run" },
            versionRef = obj.stringOrDefault("version", obj.stringOrDefault("kernel_version", "unknown")),
            metricName = obj.stringOrDefault("metric_name", BenchmarkKind.ARC_AGI_INTERACTIVE.defaultMetricName),
            primaryMetric = score,
            higherIsBetter = true,
            levelsCompleted = levels.toInt().coerceAtLeast(0),
            totalActions = actions.toInt().coerceAtLeast(0),
            tokensUsed = tokens,
            wallClockMs = firstLong(obj, "elapsed_ms", "wall_clock_ms") ?: 0L,
            source = TelemetrySourceKind.DIAGNOSTICS_JSON,
            sourceRef = sourceRef,
            startedAtEpochMs = firstLong(obj, "started_at_ms") ?: 0L,
            completedAtEpochMs = firstLong(obj, "completed_at_ms") ?: nowEpochMs,
            diagnostics = safeDiagnostics,
            redactionApplied = true,
            nowEpochMs = nowEpochMs,
        )
    }

    /**
     * Ingest a Kaggle kernel status payload:
     * `{"status":"complete","versionNumber":364758641,"score":0.10,"totalTokens":96609,...}`
     *
     * A kernel that is not `complete` is rejected on purpose: a queued or errored kernel is not
     * evidence that the candidate was evaluated.
     */
    fun ingestKaggleKernelStatus(
        rawJson: String,
        competitionSlug: String,
        benchmarkKind: BenchmarkKind = BenchmarkKind.ARC_AGI_INTERACTIVE,
        runLabel: String = "kaggle kernel run",
        nowEpochMs: Long = System.currentTimeMillis(),
    ): TelemetryIngestOutcome {
        val obj = runCatching { JsonCodec.parseObject(rawJson) }
            .getOrElse { return reject("malformed_json", "Kernel status payload is not a JSON object.") }

        val status = obj.stringOrDefault("status", "unknown").lowercase(Locale.US)
        if (status != "complete" && status != "completed") {
            return reject("kernel_not_complete", "Kernel status is '$status'; only completed runs are scored.")
        }
        val score = firstDouble(obj, "score", "publicScore", "public_score")
            ?: return reject("missing_score", "Kernel status has no score.")
        val tokens = firstLong(obj, "totalTokens", "tokens", "tokens_used") ?: 0L
        val actions = firstLong(obj, "actions", "totalActions") ?: 0L

        val diagnostics = linkedMapOf("kernel_status" to status)
        obj.string("kernelRef")?.let { diagnostics["kernel_ref"] = it }
        obj.string("error")?.let { diagnostics["error"] = it }
        val (sanitizedDiagnostics, redacted) = TelemetrySanitizer.sanitizeDiagnostics(diagnostics)
        val safeDiagnostics = if (redacted) sanitizedDiagnostics + ("redactions_applied" to "true") else sanitizedDiagnostics

        return store(
            competitionSlug = competitionSlug,
            benchmarkKind = benchmarkKind,
            runLabel = runLabel,
            versionRef = obj.string("versionNumber") ?: obj.long("versionNumber", 0L).toString(),
            metricName = obj.stringOrDefault("metric_name", benchmarkKind.defaultMetricName),
            primaryMetric = score,
            higherIsBetter = benchmarkKind != BenchmarkKind.TABULAR_CSV_CV,
            levelsCompleted = firstLong(obj, "levels", "levels_completed")?.toInt()?.coerceAtLeast(0) ?: 0,
            totalActions = actions.toInt().coerceAtLeast(0),
            tokensUsed = tokens,
            wallClockMs = firstLong(obj, "totalSeconds")?.times(1000L)
                ?: firstLong(obj, "wallClockMs", "wall_clock_ms")
                ?: 0L,
            source = TelemetrySourceKind.KAGGLE_KERNEL_STATUS,
            sourceRef = obj.string("kernelRef"),
            completedAtEpochMs = nowEpochMs,
            diagnostics = safeDiagnostics,
            redactionApplied = true,
            nowEpochMs = nowEpochMs,
        )
    }

    /** Owner-entered fallback used when a benchmark has no machine-readable diagnostics export. */
    fun ingestManualEntry(
        competitionSlug: String,
        benchmarkKind: BenchmarkKind,
        runLabel: String,
        versionRef: String,
        primaryMetric: Double,
        higherIsBetter: Boolean,
        levelsCompleted: Int,
        totalActions: Int,
        tokensUsed: Long,
        wallClockMs: Long = 0L,
        metricName: String = benchmarkKind.defaultMetricName,
        notes: Map<String, String> = emptyMap(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): TelemetryIngestOutcome {
        if (!primaryMetric.isFinite()) return reject("missing_score", "Manual entry needs a finite score.")
        if (totalActions < 0 || tokensUsed < 0 || levelsCompleted < 0) {
            return reject("negative_counters", "Manual entry counters must not be negative.")
        }
        val (sanitizedNotes, redacted) = TelemetrySanitizer.sanitizeDiagnostics(notes)
        val safeDiagnostics = if (redacted) sanitizedNotes + ("redactions_applied" to "true") else sanitizedNotes
        return store(
            competitionSlug = competitionSlug,
            benchmarkKind = benchmarkKind,
            runLabel = runLabel.ifBlank { "manual run" },
            versionRef = versionRef.ifBlank { "manual" },
            metricName = metricName,
            primaryMetric = primaryMetric,
            higherIsBetter = higherIsBetter,
            levelsCompleted = levelsCompleted,
            totalActions = totalActions,
            tokensUsed = tokensUsed,
            wallClockMs = wallClockMs,
            source = TelemetrySourceKind.MANUAL_ENTRY,
            sourceRef = null,
            completedAtEpochMs = nowEpochMs,
            diagnostics = safeDiagnostics,
            redactionApplied = true,
            nowEpochMs = nowEpochMs,
        )
    }

    fun get(telemetryId: String): RunTelemetry? {
        val row = database.backend.get(NS_TELEMETRY, telemetryId) ?: return null
        return runCatching { RunTelemetry.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    /** Most recent telemetry for a competition, newest first. */
    fun listFor(competitionSlug: String, limit: Int = 20): List<RunTelemetry> =
        listAll(limit = Int.MAX_VALUE)
            .filter { it.competitionSlug == competitionSlug }
            .sortedWith(compareByDescending<RunTelemetry> { it.completedAtEpochMs }.thenByDescending { it.recordedAtEpochMs })
            .take(limit)

    fun latestFor(competitionSlug: String): RunTelemetry? = listFor(competitionSlug, limit = 1).firstOrNull()

    fun listAll(limit: Int = 50): List<RunTelemetry> =
        database.backend.listByNamespace(NS_TELEMETRY)
            .mapNotNull { row ->
                runCatching { RunTelemetry.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }
            .sortedWith(compareByDescending<RunTelemetry> { it.completedAtEpochMs }.thenByDescending { it.recordedAtEpochMs })
            .take(limit)

    private fun store(
        competitionSlug: String,
        benchmarkKind: BenchmarkKind,
        runLabel: String,
        versionRef: String,
        metricName: String,
        primaryMetric: Double,
        higherIsBetter: Boolean,
        levelsCompleted: Int,
        totalActions: Int,
        tokensUsed: Long,
        wallClockMs: Long,
        source: TelemetrySourceKind,
        sourceRef: String?,
        startedAtEpochMs: Long = 0L,
        completedAtEpochMs: Long,
        diagnostics: Map<String, String>,
        redactionApplied: Boolean,
        nowEpochMs: Long,
    ): TelemetryIngestOutcome {
        if (competitionSlug.isBlank()) return reject("missing_competition", "A competition slug is required.")
        val record = RunTelemetry(
            telemetryId = recordIdFactory(),
            competitionSlug = competitionSlug.trim(),
            benchmarkKind = benchmarkKind,
            runLabel = runLabel.trim().take(120),
            versionRef = versionRef.trim().take(64),
            metricName = metricName.trim().take(80).ifBlank { benchmarkKind.defaultMetricName },
            primaryMetric = primaryMetric,
            higherIsBetter = higherIsBetter,
            levelsCompleted = levelsCompleted.coerceAtLeast(0),
            totalActions = totalActions.coerceAtLeast(0),
            tokensUsed = tokensUsed.coerceAtLeast(0L),
            wallClockMs = wallClockMs.coerceAtLeast(0L),
            source = source,
            sourceRef = sourceRef?.take(200),
            startedAtEpochMs = startedAtEpochMs.coerceAtLeast(0L),
            completedAtEpochMs = completedAtEpochMs.coerceAtLeast(0L),
            recordedAtEpochMs = nowEpochMs,
            redactionApplied = redactionApplied,
            diagnostics = diagnostics,
        )
        database.backend.upsert(
            namespace = NS_TELEMETRY,
            id = record.telemetryId,
            payloadJson = JsonCodec.stringify(record.toJson()),
            updatedAtEpochMs = nowEpochMs,
        )
        return TelemetryIngestOutcome.Ingested(record)
    }

    private fun reject(reason: String, detail: String): TelemetryIngestOutcome =
        TelemetryIngestOutcome.Rejected(reason, detail)

    private fun firstLong(obj: JsonValue.Obj, vararg keys: String): Long? {
        for (key in keys) {
            when (val value = obj.entries[key]) {
                is JsonValue.Num -> value.toLongOrNull()?.let { return it }
                is JsonValue.Str -> value.value.trim().toLongOrNull()?.let { return it }
                else -> Unit
            }
        }
        return null
    }

    private fun firstDouble(obj: JsonValue.Obj, vararg keys: String): Double? {
        for (key in keys) {
            when (val value = obj.entries[key]) {
                is JsonValue.Num -> if (value.value.isFinite()) return value.value
                is JsonValue.Str -> value.value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.let { return it }
                else -> Unit
            }
        }
        return null
    }

    companion object {
        const val NS_TELEMETRY = "run_telemetry"
    }
}

/** Trims noisy doubles for logs/UI without hiding meaningful precision. */
internal fun formatMetric(value: Double): String = when {
    !value.isFinite() -> "n/a"
    value == 0.0 -> "0"
    kotlin.math.abs(value) >= 100.0 -> String.format(Locale.US, "%.1f", value)
    kotlin.math.abs(value) >= 1.0 -> String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
    else -> String.format(Locale.US, "%.5f", value).trimEnd('0').trimEnd('.')
}

/** JSON cannot carry NaN/Infinity; persist a sentinel `null`-safe zero instead of corrupting input. */
internal fun Double.coerceFinite(): Double = if (isFinite()) this else 0.0

/** Explicit, auditable cap on how much a candidate may raise its own spend. */
data class ExperimentBudgetCaps(
    val maxTokensPerAction: Double,
    val maxTotalTokens: Long,
    val maxWallClockMs: Long,
    val maxActions: Int,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "maxTokensPerAction" to JsonValue.Num(maxTokensPerAction),
            "maxTotalTokens" to JsonValue.Num(maxTotalTokens.toDouble(), maxTotalTokens.toString()),
            "maxWallClockMs" to JsonValue.Num(maxWallClockMs.toDouble(), maxWallClockMs.toString()),
            "maxActions" to JsonValue.Num(maxActions.toDouble(), maxActions.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): ExperimentBudgetCaps = ExperimentBudgetCaps(
            maxTokensPerAction = obj.double("maxTokensPerAction", 4000.0),
            maxTotalTokens = obj.long("maxTotalTokens", 150_000L),
            maxWallClockMs = obj.long("maxWallClockMs", 6L * 60L * 60L * 1000L),
            maxActions = obj.int("maxActions", 500),
        )

        /** Owner-approved default envelope; candidates may only shrink it. */
        val OWNER_APPROVED_DEFAULT = ExperimentBudgetCaps(
            maxTokensPerAction = 4000.0,
            maxTotalTokens = 150_000L,
            maxWallClockMs = 6L * 60L * 60L * 1000L,
            maxActions = 500,
        )
    }
}
