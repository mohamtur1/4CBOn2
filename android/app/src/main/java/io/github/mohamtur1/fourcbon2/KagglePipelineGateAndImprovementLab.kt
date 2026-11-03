package io.github.mohamtur1.fourcbon2

import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Divisions 37, 38, and 39:
 * - Division 37: Kaggle prepare-only pipeline — deterministic trusted tabular baseline template,
 *   explicit train/test schema validation, artifact integrity checks, reproducible predictions,
 *   and zero external network submission.
 * - Division 38: Submission gate and receipt states — rolling 24h & per-run caps, durable pre-send
 *   intent ledger, acknowledgment vs. uncertain-timeout state machine (never blindly retrying
 *   ambiguous outcomes), mocked transport tests, and separate owner-gated live submission.
 * - Division 39: Minimal Improvement Lab — preserved baseline/candidate versions, fixed sealed
 *   evaluator (candidate cannot edit evaluator or promote itself), paired fold comparison,
 *   owner-approved promotion, and one-step rollback.
 */

// ============================================================================
// Division 37: Kaggle Prepare-Only Tabular Pipeline
// ============================================================================

data class TabularPipelineConfig(
    val competitionSlug: String,
    val idColumn: String = "id",
    val targetColumn: String = "target",
    val folds: Int = 3,
    val l2Regularization: Double = 1.0,
    val featureScaleMultiplier: Double = 1.0,
    val randomSeed: Long = 42L,
) {
    init {
        require(competitionSlug.isNotBlank()) { "competitionSlug must not be blank." }
        require(folds in 2..10) { "folds must be between 2 and 10." }
        require(l2Regularization >= 0.0 && l2Regularization.isFinite()) { "l2Regularization must be >= 0." }
        require(featureScaleMultiplier > 0.0 && featureScaleMultiplier.isFinite()) { "featureScaleMultiplier must be > 0." }
    }
}

data class PreparedKaggleArtifact(
    val artifactId: String,
    val competitionSlug: String,
    val idColumn: String,
    val predictionColumn: String,
    val trainRowCount: Int,
    val testRowCount: Int,
    val featureNames: List<String>,
    val foldRmseScores: List<Double>,
    val meanCvRmse: Double,
    val submissionCsv: String,
    val artifactSha256: String,
    val externalSubmissionPerformed: Boolean = false,
)

sealed interface PreparePipelineOutcome {
    data class Prepared(val artifact: PreparedKaggleArtifact) : PreparePipelineOutcome
    data class ValidationFailed(val reason: String) : PreparePipelineOutcome
}

class KagglePrepareOnlyPipeline {
    /**
     * Runs a trusted, deterministic regularized linear gradient baseline on tabular CSV data,
     * validates output schema/completeness, and returns a SHA-256-hashed submission CSV artifact
     * without performing any network call or live submission.
     */
    fun prepareTabularBaseline(
        config: TabularPipelineConfig,
        trainCsv: String,
        testCsv: String,
    ): PreparePipelineOutcome {
        val trainTable = parseSimpleCsv(trainCsv)
            ?: return PreparePipelineOutcome.ValidationFailed("Train CSV is empty or has ragged/invalid rows.")
        val testTable = parseSimpleCsv(testCsv)
            ?: return PreparePipelineOutcome.ValidationFailed("Test CSV is empty or has ragged/invalid rows.")

        val idIdxTrain = trainTable.headers.indexOf(config.idColumn)
        val targetIdxTrain = trainTable.headers.indexOf(config.targetColumn)
        if (idIdxTrain < 0 || targetIdxTrain < 0) {
            return PreparePipelineOutcome.ValidationFailed(
                "Train CSV must contain '${config.idColumn}' and '${config.targetColumn}' columns.",
            )
        }

        val featureNames = trainTable.headers.filter { it != config.idColumn && it != config.targetColumn }
        if (featureNames.isEmpty()) {
            return PreparePipelineOutcome.ValidationFailed("Train CSV contains no feature columns.")
        }

        val expectedTestHeaders = listOf(config.idColumn) + featureNames
        if (testTable.headers.toSet() != expectedTestHeaders.toSet() || testTable.headers.size != expectedTestHeaders.size) {
            return PreparePipelineOutcome.ValidationFailed(
                "Test CSV columns ${testTable.headers} do not match expected ${expectedTestHeaders}.",
            )
        }
        if (trainTable.rows.size < config.folds * 2) {
            return PreparePipelineOutcome.ValidationFailed(
                "Train CSV requires at least ${config.folds * 2} rows for ${config.folds}-fold CV.",
            )
        }
        if (testTable.rows.isEmpty()) {
            return PreparePipelineOutcome.ValidationFailed("Test CSV contains zero prediction rows.")
        }

        val idIdxTest = testTable.headers.indexOf(config.idColumn)
        val testIds = testTable.rows.map { it[idIdxTest].trim() }
        if (testIds.any { it.isEmpty() } || testIds.distinct().size != testIds.size) {
            return PreparePipelineOutcome.ValidationFailed("Test CSV contains blank or duplicate IDs.")
        }

        val trainTargets = trainTable.rows.map { row ->
            row[targetIdxTrain].toDoubleOrNull()?.takeIf(Double::isFinite)
                ?: return PreparePipelineOutcome.ValidationFailed("Train target contains non-numeric or missing value.")
        }

        val trainFeatIndices = featureNames.map { trainTable.headers.indexOf(it) }
        val testFeatIndices = featureNames.map { testTable.headers.indexOf(it) }

        // Compute deterministic per-feature training medians for missing-value imputation
        val featureMedians = DoubleArray(featureNames.size) { f ->
            val present = trainTable.rows.mapNotNull { r ->
                r[trainFeatIndices[f]].toDoubleOrNull()?.takeIf(Double::isFinite)
            }.sorted()
            if (present.isEmpty()) 0.0 else present[present.size / 2]
        }

        val xTrain = Array(trainTable.rows.size) { r ->
            DoubleArray(featureNames.size) { f ->
                val raw = trainTable.rows[r][trainFeatIndices[f]].toDoubleOrNull()?.takeIf(Double::isFinite)
                (raw ?: featureMedians[f]) * config.featureScaleMultiplier
            }
        }
        val xTest = Array(testTable.rows.size) { r ->
            DoubleArray(featureNames.size) { f ->
                val raw = testTable.rows[r][testFeatIndices[f]].toDoubleOrNull()?.takeIf(Double::isFinite)
                (raw ?: featureMedians[f]) * config.featureScaleMultiplier
            }
        }

        // Deterministic seeded index permutation for k-fold cross-validation
        val order = deterministicPermutation(trainTable.rows.size, config.randomSeed)
        val foldScores = ArrayList<Double>(config.folds)
        for (fold in 0 until config.folds) {
            val valIdx = order.filterIndexed { pos, _ -> pos % config.folds == fold }
            val trIdx = order.filterIndexed { pos, _ -> pos % config.folds != fold }
            val model = fitRegularizedLinear(
                x = trIdx.map { xTrain[it] },
                y = trIdx.map { trainTargets[it] },
                l2 = config.l2Regularization,
            )
            var sqErr = 0.0
            for (idx in valIdx) {
                val pred = model.predict(xTrain[idx])
                val diff = pred - trainTargets[idx]
                sqErr += diff * diff
            }
            foldScores.add(sqrt(sqErr / valIdx.size.toDouble()))
        }
        val meanCv = foldScores.sum() / foldScores.size.toDouble()

        val fullModel = fitRegularizedLinear(xTrain.toList(), trainTargets, config.l2Regularization)
        val predictions = xTest.map { row -> fullModel.predict(row) }
        if (predictions.any { !it.isFinite() }) {
            return PreparePipelineOutcome.ValidationFailed("Generated non-finite prediction; artifact rejected.")
        }

        val csvBuilder = StringBuilder()
        csvBuilder.append(config.idColumn).append(',').append(config.targetColumn).append('\n')
        for (i in testIds.indices) {
            csvBuilder.append(testIds[i])
                .append(',')
                .append(String.format(Locale.US, "%.6f", predictions[i]))
                .append('\n')
        }
        val submissionCsv = csvBuilder.toString()

        // Explicit artifact verification post-check
        val outputLines = submissionCsv.trimEnd().lines()
        if (outputLines.size != testIds.size + 1) {
            return PreparePipelineOutcome.ValidationFailed("Artifact line count mismatch.")
        }

        val sha = ScopedWorkspaceFileManager.sha256Hex(submissionCsv.toByteArray(Charsets.UTF_8))
        return PreparePipelineOutcome.Prepared(
            PreparedKaggleArtifact(
                artifactId = "art_${sha.take(10)}",
                competitionSlug = config.competitionSlug,
                idColumn = config.idColumn,
                predictionColumn = config.targetColumn,
                trainRowCount = trainTable.rows.size,
                testRowCount = testTable.rows.size,
                featureNames = featureNames,
                foldRmseScores = foldScores,
                meanCvRmse = meanCv,
                submissionCsv = submissionCsv,
                artifactSha256 = sha,
                externalSubmissionPerformed = false,
            ),
        )
    }

    private class LinearWeights(val bias: Double, val weights: DoubleArray, val means: DoubleArray, val scales: DoubleArray) {
        fun predict(features: DoubleArray): Double {
            var acc = bias
            for (j in weights.indices) {
                val norm = (features[j] - means[j]) / scales[j]
                acc += weights[j] * norm
            }
            return acc
        }
    }

    private fun fitRegularizedLinear(x: List<DoubleArray>, y: List<Double>, l2: Double): LinearWeights {
        val n = x.size
        val d = x.first().size
        val yMean = y.sum() / n.toDouble()
        val means = DoubleArray(d) { j -> x.sumOf { it[j] } / n.toDouble() }
        val scales = DoubleArray(d) { j ->
            val v = x.sumOf { (it[j] - means[j]) * (it[j] - means[j]) } / n.toDouble()
            val s = sqrt(v)
            if (s < 1e-9) 1.0 else s
        }
        // Coordinate / batch gradient descent on standardized features (fast, exact, deterministic)
        val w = DoubleArray(d)
        val lr = 0.08
        for (iter in 0 until 160) {
            val grad = DoubleArray(d)
            for (i in 0 until n) {
                var pred = yMean
                for (j in 0 until d) {
                    pred += w[j] * ((x[i][j] - means[j]) / scales[j])
                }
                val err = pred - y[i]
                for (j in 0 until d) {
                    grad[j] += err * ((x[i][j] - means[j]) / scales[j])
                }
            }
            for (j in 0 until d) {
                val g = (grad[j] / n.toDouble()) + l2 * w[j]
                w[j] -= lr * g
            }
        }
        return LinearWeights(bias = yMean, weights = w, means = means, scales = scales)
    }

    private data class SimpleCsvTable(val headers: List<String>, val rows: List<List<String>>)

    private fun parseSimpleCsv(csv: String): SimpleCsvTable? {
        val lines = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size < 2) return null
        val headers = lines.first().split(',').map { it.trim() }
        if (headers.any { it.isEmpty() } || headers.distinct().size != headers.size) return null
        val rows = ArrayList<List<String>>(lines.size - 1)
        for (idx in 1 until lines.size) {
            val cols = lines[idx].split(',').map { it.trim() }
            if (cols.size != headers.size) return null
            rows.add(cols)
        }
        return SimpleCsvTable(headers, rows)
    }

    companion object {
        internal fun deterministicPermutation(size: Int, seed: Long): List<Int> {
            val arr = IntArray(size) { it }
            var state = seed xor -0x61c8864680b583ebL
            for (i in size - 1 downTo 1) {
                state = state xor (state shl 13)
                state = state xor (state ushr 7)
                state = state xor (state shl 17)
                val j = ((state ushr 1) % (i + 1).toLong()).toInt()
                val tmp = arr[i]
                arr[i] = arr[j]
                arr[j] = tmp
            }
            return arr.toList()
        }
    }
}

// ============================================================================
// Division 38: Submission Gate and Receipt States
// ============================================================================

enum class SubmissionReceiptState(val label: String) {
    ARTIFACT_PREPARED_LOCAL_ONLY("Artifact Prepared (Local Only)"),
    INTENT_RECORDED_PENDING_LIVE_CALL("Intent Recorded (Awaiting Dispatch)"),
    LIVE_SUBMISSION_ACKNOWLEDGED("Submission Acknowledged by Service"),
    UNCERTAIN_ACKNOWLEDGMENT("Uncertain External Acknowledgment (Check Service Before Retry)"),
    REJECTED_BY_GATE_OR_CAP("Rejected by Local Submission Gate / Cap"),
    REJECTED_BY_SERVICE("Rejected by Remote Service"),
}

data class SubmissionIntentRecord(
    val intentId: String,
    val runId: String,
    val competitionSlug: String,
    val artifactSha256: String,
    val state: SubmissionReceiptState,
    val externalReceiptRef: String?,
    val statusNote: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "intentId" to JsonValue.Str(intentId),
            "runId" to JsonValue.Str(runId),
            "competitionSlug" to JsonValue.Str(competitionSlug),
            "artifactSha256" to JsonValue.Str(artifactSha256),
            "state" to JsonValue.Str(state.name),
            "externalReceiptRef" to (externalReceiptRef?.let(JsonValue::Str) ?: JsonValue.Null),
            "statusNote" to JsonValue.Str(statusNote),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
            "updatedAtEpochMs" to JsonValue.Num(updatedAtEpochMs.toDouble(), updatedAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): SubmissionIntentRecord = SubmissionIntentRecord(
            intentId = obj.stringOrDefault("intentId"),
            runId = obj.stringOrDefault("runId"),
            competitionSlug = obj.stringOrDefault("competitionSlug"),
            artifactSha256 = obj.stringOrDefault("artifactSha256"),
            state = runCatching { SubmissionReceiptState.valueOf(obj.stringOrDefault("state")) }
                .getOrDefault(SubmissionReceiptState.ARTIFACT_PREPARED_LOCAL_ONLY),
            externalReceiptRef = obj.string("externalReceiptRef"),
            statusNote = obj.stringOrDefault("statusNote"),
            createdAtEpochMs = obj.long("createdAtEpochMs", 0L),
            updatedAtEpochMs = obj.long("updatedAtEpochMs", 0L),
        )
    }
}

sealed interface KaggleSubmissionTransportResponse {
    data class Acknowledged(val submissionReferenceId: String) : KaggleSubmissionTransportResponse
    data class ServiceRejected(val httpStatus: Int, val message: String) : KaggleSubmissionTransportResponse
    data class AmbiguousTimeoutOrDrop(val message: String) : KaggleSubmissionTransportResponse
}

fun interface KaggleSubmissionTransport {
    @Throws(IOException::class)
    fun submitPreparedCsv(competitionSlug: String, submissionCsv: String, artifactSha256: String): KaggleSubmissionTransportResponse
}

class KaggleSubmissionGate(
    private val database: FourCBOn2Database,
    private val approvalGate: SensitiveActionApprovalGate,
    private val leaseController: TakeoverLeaseController,
    private val timeline: ActionTimelineRepository,
    private val maxSubmissionsPerRun: Int = 1,
    private val maxSubmissionsPerRolling24h: Int = 1,
) {
    fun recordPreparedOnly(
        runId: String,
        artifact: PreparedKaggleArtifact,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): SubmissionIntentRecord {
        val rec = SubmissionIntentRecord(
            intentId = "sub_${UUID.randomUUID().toString().take(8)}",
            runId = runId,
            competitionSlug = artifact.competitionSlug,
            artifactSha256 = artifact.artifactSha256,
            state = SubmissionReceiptState.ARTIFACT_PREPARED_LOCAL_ONLY,
            externalReceiptRef = null,
            statusNote = "Prepared ${artifact.testRowCount} rows (CV RMSE=${String.format(Locale.US, "%.4f", artifact.meanCvRmse)}); no live submission sent.",
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
        return persist(rec)
    }

    /**
     * Executes a gated submission attempt only when:
     * 1. `liveSubmissionAuthorized == true`,
     * 2. No unresolved `UNCERTAIN_ACKNOWLEDGMENT` or duplicate submitted SHA-256 exists,
     * 3. Per-run (`maxSubmissionsPerRun`) and rolling 24h (`maxSubmissionsPerRolling24h`) caps pass,
     * 4. Active `TakeoverLeaseController` lease is valid, and
     * 5. Confirmed `SensitiveActionApprovalGate` grant matches `kaggle:<slug>` + `artifact.artifactSha256`.
     */
    fun submitWithGate(
        runId: String,
        artifact: PreparedKaggleArtifact,
        liveSubmissionAuthorized: Boolean,
        approvalGrantId: String?,
        sessionId: String,
        leaseEpoch: Long,
        transport: KaggleSubmissionTransport,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): SubmissionIntentRecord {
        if (!liveSubmissionAuthorized) {
            return recordGateRejection(
                runId = runId,
                artifact = artifact,
                reason = "Live submission is disabled (prepare-only mode active).",
                nowEpochMs = nowEpochMs,
            )
        }

        when (val lease = leaseController.validateCommand(sessionId, leaseEpoch)) {
            is LeaseValidationOutcome.RejectedStaleOrCancelled ->
                return recordGateRejection(runId, artifact, lease.reason, nowEpochMs)
            is LeaseValidationOutcome.Valid -> Unit
        }

        val history = listRecords()

        // Block any submission while an uncertain acknowledgment is unresolved for this competition
        if (history.any {
                it.competitionSlug == artifact.competitionSlug &&
                    it.state == SubmissionReceiptState.UNCERTAIN_ACKNOWLEDGMENT
            }
        ) {
            return recordGateRejection(
                runId = runId,
                artifact = artifact,
                reason = "Blocked: previous submission for '${artifact.competitionSlug}' has an unresolved UNCERTAIN_ACKNOWLEDGMENT.",
                nowEpochMs = nowEpochMs,
            )
        }

        // Deduplicate identical artifact SHA-256 already acknowledged
        if (history.any {
                it.competitionSlug == artifact.competitionSlug &&
                    it.artifactSha256 == artifact.artifactSha256 &&
                    it.state == SubmissionReceiptState.LIVE_SUBMISSION_ACKNOWLEDGED
            }
        ) {
            return recordGateRejection(
                runId = runId,
                artifact = artifact,
                reason = "Duplicate submission blocked: artifact ${artifact.artifactSha256.take(10)}… was already acknowledged.",
                nowEpochMs = nowEpochMs,
            )
        }

        val countedStates = setOf(
            SubmissionReceiptState.LIVE_SUBMISSION_ACKNOWLEDGED,
            SubmissionReceiptState.UNCERTAIN_ACKNOWLEDGMENT,
        )
        val usedInRun = history.count { it.runId == runId && it.state in countedStates }
        if (usedInRun >= maxSubmissionsPerRun) {
            return recordGateRejection(
                runId = runId,
                artifact = artifact,
                reason = "Per-run submission cap ($maxSubmissionsPerRun) reached.",
                nowEpochMs = nowEpochMs,
            )
        }

        val windowStart = nowEpochMs - 24L * 60L * 60L * 1000L
        val usedIn24h = history.count { it.createdAtEpochMs >= windowStart && it.state in countedStates }
        if (usedIn24h >= maxSubmissionsPerRolling24h) {
            return recordGateRejection(
                runId = runId,
                artifact = artifact,
                reason = "Rolling 24-hour submission cap ($maxSubmissionsPerRolling24h) reached.",
                nowEpochMs = nowEpochMs,
            )
        }

        if (approvalGrantId.isNullOrBlank()) {
            return recordGateRejection(runId, artifact, "Missing explicit owner approval grant ID.", nowEpochMs)
        }
        val approval = approvalGate.consumeApproval(
            grantId = approvalGrantId,
            expectedActionType = ACTION_SUBMIT_KAGGLE,
            expectedDestination = "kaggle:${artifact.competitionSlug}",
            expectedArtifactSha256 = artifact.artifactSha256,
            nowEpochMs = nowEpochMs,
        )
        if (approval is ApprovalGateResult.Denied) {
            return recordGateRejection(runId, artifact, approval.reason, nowEpochMs)
        }

        // Write durable pre-send intent BEFORE invoking external transport!
        val pendingIntent = persist(
            SubmissionIntentRecord(
                intentId = "sub_${UUID.randomUUID().toString().take(8)}",
                runId = runId,
                competitionSlug = artifact.competitionSlug,
                artifactSha256 = artifact.artifactSha256,
                state = SubmissionReceiptState.INTENT_RECORDED_PENDING_LIVE_CALL,
                externalReceiptRef = null,
                statusNote = "Durable submission intent recorded prior to network call.",
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
            ),
        )

        val response = try {
            transport.submitPreparedCsv(
                competitionSlug = artifact.competitionSlug,
                submissionCsv = artifact.submissionCsv,
                artifactSha256 = artifact.artifactSha256,
            )
        } catch (ex: IOException) {
            KaggleSubmissionTransportResponse.AmbiguousTimeoutOrDrop(ex.message ?: "Transport I/O interruption.")
        }

        val finalized = when (response) {
            is KaggleSubmissionTransportResponse.Acknowledged -> pendingIntent.copy(
                state = SubmissionReceiptState.LIVE_SUBMISSION_ACKNOWLEDGED,
                externalReceiptRef = response.submissionReferenceId,
                statusNote = "Submission acknowledged (ref=${response.submissionReferenceId}); leaderboard scoring remains separate.",
                updatedAtEpochMs = nowEpochMs + 1,
            )
            is KaggleSubmissionTransportResponse.ServiceRejected -> pendingIntent.copy(
                state = SubmissionReceiptState.REJECTED_BY_SERVICE,
                statusNote = "Service rejected submission (HTTP ${response.httpStatus}): ${response.message}",
                updatedAtEpochMs = nowEpochMs + 1,
            )
            is KaggleSubmissionTransportResponse.AmbiguousTimeoutOrDrop -> pendingIntent.copy(
                state = SubmissionReceiptState.UNCERTAIN_ACKNOWLEDGMENT,
                statusNote = "Ambiguous network outcome (${response.message}); automatic retry locked until owner reconciles.",
                updatedAtEpochMs = nowEpochMs + 1,
            )
        }

        timeline.recordEvent(
            stage = TimelineStage.VERIFICATION,
            actor = TimelineActor.LOCAL_BROKER,
            summary = "Submission gate outcome for ${artifact.competitionSlug}: ${finalized.state.label}",
            outcome = when (finalized.state) {
                SubmissionReceiptState.LIVE_SUBMISSION_ACKNOWLEDGED -> TimelineOutcomeState.VERIFIED
                SubmissionReceiptState.UNCERTAIN_ACKNOWLEDGMENT -> TimelineOutcomeState.UNCERTAIN
                else -> TimelineOutcomeState.FAILED
            },
            targetReference = finalized.externalReceiptRef ?: artifact.competitionSlug,
            nowEpochMs = nowEpochMs + 1,
        )
        return persist(finalized)
    }

    fun reconcileUncertainSubmission(
        intentId: String,
        confirmedReferenceId: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): SubmissionIntentRecord? {
        val existing = getRecord(intentId) ?: return null
        if (existing.state != SubmissionReceiptState.UNCERTAIN_ACKNOWLEDGMENT) return existing
        return persist(
            if (!confirmedReferenceId.isNullOrBlank()) {
                existing.copy(
                    state = SubmissionReceiptState.LIVE_SUBMISSION_ACKNOWLEDGED,
                    externalReceiptRef = confirmedReferenceId.trim(),
                    statusNote = "Reconciled by owner with receipt $confirmedReferenceId.",
                    updatedAtEpochMs = nowEpochMs,
                )
            } else {
                existing.copy(
                    state = SubmissionReceiptState.REJECTED_BY_SERVICE,
                    statusNote = "Owner confirmed submission was not received by Kaggle.",
                    updatedAtEpochMs = nowEpochMs,
                )
            },
        )
    }

    fun getRecord(intentId: String): SubmissionIntentRecord? {
        val row = database.backend.get(NS_SUBMISSION_LEDGER, intentId) ?: return null
        return runCatching { SubmissionIntentRecord.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun listRecords(): List<SubmissionIntentRecord> =
        database.backend.listByNamespace(NS_SUBMISSION_LEDGER)
            .mapNotNull { row ->
                runCatching { SubmissionIntentRecord.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }

    private fun recordGateRejection(
        runId: String,
        artifact: PreparedKaggleArtifact,
        reason: String,
        nowEpochMs: Long,
    ): SubmissionIntentRecord {
        timeline.recordEvent(
            stage = TimelineStage.APPROVAL_CHECK,
            actor = TimelineActor.LOCAL_BROKER,
            summary = "Submission gate blocked: $reason",
            outcome = TimelineOutcomeState.BLOCKED,
            targetReference = artifact.competitionSlug,
            nowEpochMs = nowEpochMs,
        )
        return persist(
            SubmissionIntentRecord(
                intentId = "sub_${UUID.randomUUID().toString().take(8)}",
                runId = runId,
                competitionSlug = artifact.competitionSlug,
                artifactSha256 = artifact.artifactSha256,
                state = SubmissionReceiptState.REJECTED_BY_GATE_OR_CAP,
                externalReceiptRef = null,
                statusNote = reason,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs,
            ),
        )
    }

    private fun persist(rec: SubmissionIntentRecord): SubmissionIntentRecord {
        database.backend.upsert(
            namespace = NS_SUBMISSION_LEDGER,
            id = rec.intentId,
            payloadJson = JsonCodec.stringify(rec.toJson()),
            updatedAtEpochMs = rec.updatedAtEpochMs,
        )
        return rec
    }

    companion object {
        const val NS_SUBMISSION_LEDGER = "kaggle_submission_ledger"
        const val ACTION_SUBMIT_KAGGLE = "KAGGLE_LIVE_SUBMISSION"
    }
}

// ============================================================================
// Division 39: Minimal Improvement Lab (Separated Optimizer, Fixed Evaluator & Owner Promotion)
// ============================================================================

enum class CandidatePromotionState(val label: String) {
    PENDING_OWNER_APPROVAL("Eligible — Pending Owner Approval"),
    REJECTED_BY_SEALED_EVALUATOR("Rejected by Fixed Evaluator (Baseline Kept)"),
    PROMOTED_BY_OWNER("Promoted by Owner"),
    ROLLED_BACK("Rolled Back to Prior Baseline"),
}

data class LabConfigurationVersion(
    val versionId: String,
    val parentVersionId: String?,
    val label: String,
    val l2Regularization: Double,
    val featureScaleMultiplier: Double,
    val createdAtEpochMs: Long,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "versionId" to JsonValue.Str(versionId),
            "parentVersionId" to (parentVersionId?.let(JsonValue::Str) ?: JsonValue.Null),
            "label" to JsonValue.Str(label),
            "l2Regularization" to JsonValue.Num(l2Regularization),
            "featureScaleMultiplier" to JsonValue.Num(featureScaleMultiplier),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): LabConfigurationVersion = LabConfigurationVersion(
            versionId = obj.stringOrDefault("versionId"),
            parentVersionId = obj.string("parentVersionId"),
            label = obj.stringOrDefault("label"),
            l2Regularization = obj.double("l2Regularization", 1.0),
            featureScaleMultiplier = obj.double("featureScaleMultiplier", 1.0),
            createdAtEpochMs = obj.long("createdAtEpochMs", 0L),
        )
    }
}

data class ImprovementComparisonReport(
    val reportId: String,
    val baselineVersionId: String,
    val candidateVersionId: String,
    val metricName: String = "CV_RMSE (lower is better)",
    val baselineMeanRmse: Double,
    val candidateMeanRmse: Double,
    val rmseImprovement: Double,
    val foldsImprovedCount: Int,
    val totalFolds: Int,
    val passesFixedEvaluator: Boolean,
    val state: CandidatePromotionState,
    val createdAtEpochMs: Long,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "reportId" to JsonValue.Str(reportId),
            "baselineVersionId" to JsonValue.Str(baselineVersionId),
            "candidateVersionId" to JsonValue.Str(candidateVersionId),
            "metricName" to JsonValue.Str(metricName),
            "baselineMeanRmse" to JsonValue.Num(baselineMeanRmse),
            "candidateMeanRmse" to JsonValue.Num(candidateMeanRmse),
            "rmseImprovement" to JsonValue.Num(rmseImprovement),
            "foldsImprovedCount" to JsonValue.Num(foldsImprovedCount.toDouble(), foldsImprovedCount.toString()),
            "totalFolds" to JsonValue.Num(totalFolds.toDouble(), totalFolds.toString()),
            "passesFixedEvaluator" to JsonValue.Bool(passesFixedEvaluator),
            "state" to JsonValue.Str(state.name),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): ImprovementComparisonReport = ImprovementComparisonReport(
            reportId = obj.stringOrDefault("reportId"),
            baselineVersionId = obj.stringOrDefault("baselineVersionId"),
            candidateVersionId = obj.stringOrDefault("candidateVersionId"),
            metricName = obj.stringOrDefault("metricName", "CV_RMSE (lower is better)"),
            baselineMeanRmse = obj.double("baselineMeanRmse", 0.0),
            candidateMeanRmse = obj.double("candidateMeanRmse", 0.0),
            rmseImprovement = obj.double("rmseImprovement", 0.0),
            foldsImprovedCount = obj.int("foldsImprovedCount", 0),
            totalFolds = obj.int("totalFolds", 3),
            passesFixedEvaluator = obj.bool("passesFixedEvaluator", false),
            state = runCatching { CandidatePromotionState.valueOf(obj.stringOrDefault("state")) }
                .getOrDefault(CandidatePromotionState.REJECTED_BY_SEALED_EVALUATOR),
            createdAtEpochMs = obj.long("createdAtEpochMs", 0L),
        )
    }
}

class MinimalImprovementLab(
    private val database: FourCBOn2Database,
    private val pipeline: KagglePrepareOnlyPipeline = KagglePrepareOnlyPipeline(),
    private val minRequiredRmseGain: Double = 1e-4,
) {
    fun registerBaseline(
        label: String,
        l2Regularization: Double,
        featureScaleMultiplier: Double = 1.0,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): LabConfigurationVersion {
        val v = LabConfigurationVersion(
            versionId = "ver_base_${UUID.randomUUID().toString().take(6)}",
            parentVersionId = null,
            label = label.trim(),
            l2Regularization = l2Regularization,
            featureScaleMultiplier = featureScaleMultiplier,
            createdAtEpochMs = nowEpochMs,
        )
        saveVersion(v)
        if (activePromotedVersionId() == null) {
            database.putSetting(KEY_ACTIVE_VERSION, v.versionId, nowEpochMs)
        }
        return v
    }

    fun proposeCandidate(
        parentBaselineId: String,
        label: String,
        l2Regularization: Double,
        featureScaleMultiplier: Double = 1.0,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): LabConfigurationVersion {
        require(getVersion(parentBaselineId) != null) { "Parent baseline '$parentBaselineId' does not exist." }
        val candidate = LabConfigurationVersion(
            versionId = "ver_cand_${UUID.randomUUID().toString().take(6)}",
            parentVersionId = parentBaselineId,
            label = label.trim(),
            l2Regularization = l2Regularization,
            featureScaleMultiplier = featureScaleMultiplier,
            createdAtEpochMs = nowEpochMs,
        )
        return saveVersion(candidate)
    }

    /**
     * Evaluates a candidate against its preserved baseline using the FIXED evaluator seed & folds.
     * Even when `passesFixedEvaluator == true`, active version NEVER changes until [approvePromotion]!
     */
    fun evaluateCandidateAgainstBaseline(
        baselineId: String,
        candidateId: String,
        sealedTrainCsv: String,
        sealedTestCsv: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ImprovementComparisonReport {
        val baseVer = getVersion(baselineId) ?: error("Missing baseline version $baselineId")
        val candVer = getVersion(candidateId) ?: error("Missing candidate version $candidateId")

        val baseRes = pipeline.prepareTabularBaseline(
            config = TabularPipelineConfig(
                competitionSlug = "lab-eval",
                folds = FIXED_EVAL_FOLDS,
                l2Regularization = baseVer.l2Regularization,
                featureScaleMultiplier = baseVer.featureScaleMultiplier,
                randomSeed = FIXED_EVAL_SEED,
            ),
            trainCsv = sealedTrainCsv,
            testCsv = sealedTestCsv,
        ) as? PreparePipelineOutcome.Prepared
            ?: error("Fixed evaluator dataset failed validation on baseline.")

        val candRes = pipeline.prepareTabularBaseline(
            config = TabularPipelineConfig(
                competitionSlug = "lab-eval",
                folds = FIXED_EVAL_FOLDS,
                l2Regularization = candVer.l2Regularization,
                featureScaleMultiplier = candVer.featureScaleMultiplier,
                randomSeed = FIXED_EVAL_SEED,
            ),
            trainCsv = sealedTrainCsv,
            testCsv = sealedTestCsv,
        ) as? PreparePipelineOutcome.Prepared
            ?: error("Fixed evaluator dataset failed validation on candidate.")

        val gain = baseRes.artifact.meanCvRmse - candRes.artifact.meanCvRmse
        val improvedFolds = baseRes.artifact.foldRmseScores.zip(candRes.artifact.foldRmseScores)
            .count { (b, c) -> c < b - 1e-9 || abs(c - b) < 1e-12 && gain > minRequiredRmseGain }
        val majorityFoldsImproved = improvedFolds >= (FIXED_EVAL_FOLDS / 2) + 1
        val passes = gain >= minRequiredRmseGain && majorityFoldsImproved

        val report = ImprovementComparisonReport(
            reportId = "rep_${UUID.randomUUID().toString().take(8)}",
            baselineVersionId = baselineId,
            candidateVersionId = candidateId,
            baselineMeanRmse = baseRes.artifact.meanCvRmse,
            candidateMeanRmse = candRes.artifact.meanCvRmse,
            rmseImprovement = gain,
            foldsImprovedCount = improvedFolds,
            totalFolds = FIXED_EVAL_FOLDS,
            passesFixedEvaluator = passes,
            state = if (passes) {
                CandidatePromotionState.PENDING_OWNER_APPROVAL
            } else {
                CandidatePromotionState.REJECTED_BY_SEALED_EVALUATOR
            },
            createdAtEpochMs = nowEpochMs,
        )
        return saveReport(report)
    }

    /**
     * Promotes a candidate ONLY if it passed the fixed evaluator AND `approvedByOwner == true`.
     * Rejects self-promotion attempts (`approvedByOwner == false`) unconditionally!
     */
    fun approvePromotion(
        reportId: String,
        approvedByOwner: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ImprovementComparisonReport {
        require(approvedByOwner) { "Candidates cannot self-promote; explicit owner approval is mandatory." }
        val report = getReport(reportId) ?: error("Comparison report '$reportId' not found.")
        if (!report.passesFixedEvaluator) {
            return report
        }
        database.putSetting(KEY_ROLLBACK_VERSION, report.baselineVersionId, nowEpochMs)
        database.putSetting(KEY_ACTIVE_VERSION, report.candidateVersionId, nowEpochMs)
        return saveReport(report.copy(state = CandidatePromotionState.PROMOTED_BY_OWNER))
    }

    fun rollbackToPreviousBaseline(nowEpochMs: Long = System.currentTimeMillis()): String? {
        val rollbackId = database.getSetting(KEY_ROLLBACK_VERSION) ?: return null
        database.putSetting(KEY_ACTIVE_VERSION, rollbackId, nowEpochMs)
        return rollbackId
    }

    fun activePromotedVersionId(): String? = database.getSetting(KEY_ACTIVE_VERSION)

    fun getVersion(versionId: String): LabConfigurationVersion? {
        val row = database.backend.get(NS_LAB_VERSIONS, versionId) ?: return null
        return runCatching { LabConfigurationVersion.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun listVersions(): List<LabConfigurationVersion> =
        database.backend.listByNamespace(NS_LAB_VERSIONS)
            .mapNotNull { row ->
                runCatching { LabConfigurationVersion.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }

    fun getReport(reportId: String): ImprovementComparisonReport? {
        val row = database.backend.get(NS_LAB_REPORTS, reportId) ?: return null
        return runCatching { ImprovementComparisonReport.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun listReports(): List<ImprovementComparisonReport> =
        database.backend.listByNamespace(NS_LAB_REPORTS)
            .mapNotNull { row ->
                runCatching { ImprovementComparisonReport.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }

    private fun saveVersion(v: LabConfigurationVersion): LabConfigurationVersion {
        database.backend.upsert(NS_LAB_VERSIONS, v.versionId, JsonCodec.stringify(v.toJson()), v.createdAtEpochMs)
        return v
    }

    private fun saveReport(r: ImprovementComparisonReport): ImprovementComparisonReport {
        database.backend.upsert(NS_LAB_REPORTS, r.reportId, JsonCodec.stringify(r.toJson()), r.createdAtEpochMs)
        return r
    }

    companion object {
        const val NS_LAB_VERSIONS = "improvement_lab_versions"
        const val NS_LAB_REPORTS = "improvement_lab_reports"
        private const val KEY_ACTIVE_VERSION = "improvement_lab_active_version"
        private const val KEY_ROLLBACK_VERSION = "improvement_lab_rollback_version"
        private const val FIXED_EVAL_FOLDS = 3
        private const val FIXED_EVAL_SEED = 20261031L
    }
}
