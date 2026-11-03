package io.github.mohamtur1.fourcbon2

import java.util.UUID

/**
 * Divisions 28 & 30 (Android side):
 * - Division 28: Shared Job Bundle (`4cbon2.job_bundle.v1`) — versioned app/notebook contract,
 *   manifest, private export/import, SHA-256 integrity checks, secret stripping, and mandatory
 *   local policy revalidation on import ("a hash is integrity, NOT authority").
 * - Division 30: Colab built-in AI adapter boundary — tracks real probe/bundle status for
 *   `google.colab.ai`, enforces bounded call/char budgets, never substitutes another provider,
 *   and blocks any Colab proxy-credential import/export.
 */
enum class BundleExecutionRole {
    ANDROID_COMPANION,
    COLAB_CONTROLLER,
    KAGGLE_RUNNER,
}

data class BundleArtifactEntry(
    val artifactId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256Hex: String,
) {
    init {
        require(fileName.isNotBlank() && !fileName.contains("..") && !fileName.contains('/') && !fileName.contains('\\')) {
            "Unsafe artifact filename in job bundle: $fileName"
        }
        require(SHA256_REGEX.matches(sha256Hex.lowercase())) {
            "Artifact sha256Hex must be a 64-char hex digest."
        }
    }

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "artifactId" to JsonValue.Str(artifactId),
            "fileName" to JsonValue.Str(fileName),
            "mimeType" to JsonValue.Str(mimeType),
            "sizeBytes" to JsonValue.Num(sizeBytes.toDouble(), sizeBytes.toString()),
            "sha256Hex" to JsonValue.Str(sha256Hex.lowercase()),
        ),
    )

    companion object {
        private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")

        fun fromJson(obj: JsonValue.Obj): BundleArtifactEntry = BundleArtifactEntry(
            artifactId = obj.stringOrDefault("artifactId"),
            fileName = obj.stringOrDefault("fileName"),
            mimeType = obj.stringOrDefault("mimeType", "application/octet-stream"),
            sizeBytes = obj.long("sizeBytes", 0L),
            sha256Hex = obj.stringOrDefault("sha256Hex").lowercase(),
        )
    }
}

data class SharedJobBundleManifest(
    val schemaVersion: String = SCHEMA_V1,
    val bundleId: String,
    val jobId: String,
    val title: String,
    val goal: String,
    val profile: JobProfile,
    val sourceRole: BundleExecutionRole,
    val targetRole: BundleExecutionRole,
    val maxSteps: Int,
    val maxModelCalls: Int,
    val maxRuntimeMinutes: Int,
    val requestedActions: List<ScopedActionType>,
    val artifacts: List<BundleArtifactEntry>,
    val createdAtEpochMs: Long,
    val manifestSha256: String,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "schemaVersion" to JsonValue.Str(schemaVersion),
            "bundleId" to JsonValue.Str(bundleId),
            "jobId" to JsonValue.Str(jobId),
            "title" to JsonValue.Str(title),
            "goal" to JsonValue.Str(goal),
            "profile" to JsonValue.Str(profile.name),
            "sourceRole" to JsonValue.Str(sourceRole.name),
            "targetRole" to JsonValue.Str(targetRole.name),
            "maxSteps" to JsonValue.Num(maxSteps.toDouble(), maxSteps.toString()),
            "maxModelCalls" to JsonValue.Num(maxModelCalls.toDouble(), maxModelCalls.toString()),
            "maxRuntimeMinutes" to JsonValue.Num(maxRuntimeMinutes.toDouble(), maxRuntimeMinutes.toString()),
            "requestedActions" to JsonValue.Arr(requestedActions.map { JsonValue.Str(it.name) }),
            "artifacts" to JsonValue.Arr(artifacts.map { it.toJson() }),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
            "manifestSha256" to JsonValue.Str(manifestSha256.lowercase()),
        ),
    )

    companion object {
        const val SCHEMA_V1 = "4cbon2.job_bundle.v1"

        internal fun canonicalPayloadJson(
            schemaVersion: String,
            bundleId: String,
            jobId: String,
            title: String,
            goal: String,
            profile: JobProfile,
            sourceRole: BundleExecutionRole,
            targetRole: BundleExecutionRole,
            maxSteps: Int,
            maxModelCalls: Int,
            maxRuntimeMinutes: Int,
            requestedActions: List<ScopedActionType>,
            artifacts: List<BundleArtifactEntry>,
            createdAtEpochMs: Long,
        ): String = JsonCodec.stringify(
            JsonValue.Obj(
                linkedMapOf(
                    "schemaVersion" to JsonValue.Str(schemaVersion),
                    "bundleId" to JsonValue.Str(bundleId),
                    "jobId" to JsonValue.Str(jobId),
                    "title" to JsonValue.Str(title),
                    "goal" to JsonValue.Str(goal),
                    "profile" to JsonValue.Str(profile.name),
                    "sourceRole" to JsonValue.Str(sourceRole.name),
                    "targetRole" to JsonValue.Str(targetRole.name),
                    "maxSteps" to JsonValue.Num(maxSteps.toDouble(), maxSteps.toString()),
                    "maxModelCalls" to JsonValue.Num(maxModelCalls.toDouble(), maxModelCalls.toString()),
                    "maxRuntimeMinutes" to JsonValue.Num(maxRuntimeMinutes.toDouble(), maxRuntimeMinutes.toString()),
                    "requestedActions" to JsonValue.Arr(requestedActions.map { JsonValue.Str(it.name) }),
                    "artifacts" to JsonValue.Arr(artifacts.map { it.toJson() }),
                    "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
                ),
            ),
        )
    }
}

data class RevalidatedJobBundle(
    val manifest: SharedJobBundleManifest,
    val locallyPermittedActions: Set<ScopedActionType>,
    val deniedRequestedActions: Set<ScopedActionType>,
    val requiresFreshOwnerApproval: Boolean,
)

sealed interface BundleImportResult {
    data class VerifiedAndRevalidated(val bundle: RevalidatedJobBundle) : BundleImportResult
    data class Rejected(val reason: String) : BundleImportResult
}

class SharedJobBundleManager(
    private val workspaceFiles: ScopedWorkspaceFileManager,
) {
    /**
     * Exports a private, credential-free [SharedJobBundleManifest] JSON string from an [AgentDraft].
     */
    fun createBundleFromDraft(
        draft: AgentDraft,
        targetRole: BundleExecutionRole = BundleExecutionRole.COLAB_CONTROLLER,
        requestedActions: List<ScopedActionType> = emptyList(),
        attachedFileIds: List<String> = emptyList(),
        maxModelCalls: Int = defaultModelCallCap(draft.profile),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): SharedJobBundleManifest {
        val cleanTitle = ConsentedObservationBoundary.redactSecrets(draft.title.trim())
        val cleanGoal = ConsentedObservationBoundary.redactSecrets(draft.goal.trim())
        require(!containsForbiddenSecretMarkers(cleanTitle) && !containsForbiddenSecretMarkers(cleanGoal)) {
            "Bundle export rejected: detected secret/proxy-token marker."
        }

        val artifacts = attachedFileIds.mapNotNull { id ->
            val rec = workspaceFiles.getRecord(id) ?: return@mapNotNull null
            // Verify on-disk integrity before referencing in manifest
            if (workspaceFiles.readVerifiedBytes(id) == null) return@mapNotNull null
            BundleArtifactEntry(
                artifactId = rec.id,
                fileName = rec.displayName,
                mimeType = rec.mimeType,
                sizeBytes = rec.sizeBytes,
                sha256Hex = rec.sha256Hex,
            )
        }

        val bundleId = "bundle_${UUID.randomUUID().toString().take(8)}"
        val jobId = "job_${draft.id.removePrefix("draft_")}"
        val boundedSteps = draft.caps.maxSteps.coerceIn(1, 100)
        val boundedCalls = maxModelCalls.coerceIn(0, 24)
        val boundedMinutes = draft.caps.maxRuntimeMinutes.coerceIn(1, 60)

        val canonical = SharedJobBundleManifest.canonicalPayloadJson(
            schemaVersion = SharedJobBundleManifest.SCHEMA_V1,
            bundleId = bundleId,
            jobId = jobId,
            title = cleanTitle,
            goal = cleanGoal,
            profile = draft.profile,
            sourceRole = BundleExecutionRole.ANDROID_COMPANION,
            targetRole = targetRole,
            maxSteps = boundedSteps,
            maxModelCalls = boundedCalls,
            maxRuntimeMinutes = boundedMinutes,
            requestedActions = requestedActions,
            artifacts = artifacts,
            createdAtEpochMs = nowEpochMs,
        )
        val digest = ScopedWorkspaceFileManager.sha256Hex(canonical.toByteArray(Charsets.UTF_8))

        return SharedJobBundleManifest(
            schemaVersion = SharedJobBundleManifest.SCHEMA_V1,
            bundleId = bundleId,
            jobId = jobId,
            title = cleanTitle,
            goal = cleanGoal,
            profile = draft.profile,
            sourceRole = BundleExecutionRole.ANDROID_COMPANION,
            targetRole = targetRole,
            maxSteps = boundedSteps,
            maxModelCalls = boundedCalls,
            maxRuntimeMinutes = boundedMinutes,
            requestedActions = requestedActions,
            artifacts = artifacts,
            createdAtEpochMs = nowEpochMs,
            manifestSha256 = digest,
        )
    }

    fun serializeBundle(manifest: SharedJobBundleManifest): String =
        JsonCodec.stringify(manifest.toJson())

    /**
     * Imports and revalidates an external/notebook bundle JSON.
     *
     * Key security rule (Division 28):
     * - Verifies exact schema version, embedded secret absence, artifact hashes, and `manifestSha256`.
     * - **A hash is NOT authority**: any embedded `"approved": true`, `"grantAuthority": true`, or
     *   unpermitted action in the JSON never grants execution rights—actions are intersected with
     *   [localScopeContract] (if supplied) and external effects always require fresh owner approval!
     */
    fun importAndRevalidate(
        rawJson: String,
        suppliedArtifactHashesByName: Map<String, String> = emptyMap(),
        localScopeContract: TaskScopeContract? = null,
    ): BundleImportResult {
        if (rawJson.isBlank() || rawJson.length > MAX_BUNDLE_JSON_CHARS) {
            return BundleImportResult.Rejected("Bundle JSON is empty or exceeds size limit.")
        }
        if (containsForbiddenSecretMarkers(rawJson)) {
            return BundleImportResult.Rejected("Bundle rejected: contains forbidden credential or Colab proxy token.")
        }

        val obj = try {
            JsonCodec.parseObject(rawJson)
        } catch (ex: JsonParseException) {
            return BundleImportResult.Rejected("Malformed bundle JSON: ${ex.message}")
        }

        // Explicitly reject bundles attempting to smuggle credential/secret keys
        val forbiddenKeys = obj.entries.keys.filter { k ->
            val lower = k.lowercase()
            lower.contains("secret") || lower.contains("password") || lower.contains("token") ||
                lower.contains("colab_proxy") || lower.contains("api_key")
        }
        if (forbiddenKeys.isNotEmpty()) {
            return BundleImportResult.Rejected("Bundle contains prohibited secret field(s): $forbiddenKeys")
        }

        val version = obj.stringOrDefault("schemaVersion")
        if (version != SharedJobBundleManifest.SCHEMA_V1) {
            return BundleImportResult.Rejected("Unsupported bundle schema version: '$version'.")
        }

        val profile = runCatching { JobProfile.valueOf(obj.stringOrDefault("profile")) }.getOrNull()
            ?: return BundleImportResult.Rejected("Invalid job profile in bundle.")
        val sourceRole = runCatching { BundleExecutionRole.valueOf(obj.stringOrDefault("sourceRole")) }.getOrNull()
            ?: return BundleImportResult.Rejected("Invalid sourceRole in bundle.")
        val targetRole = runCatching { BundleExecutionRole.valueOf(obj.stringOrDefault("targetRole")) }.getOrNull()
            ?: return BundleImportResult.Rejected("Invalid targetRole in bundle.")

        val requestedActions = ArrayList<ScopedActionType>()
        for (actionName in obj.stringList("requestedActions")) {
            val parsed = runCatching { ScopedActionType.valueOf(actionName) }.getOrNull()
                ?: return BundleImportResult.Rejected("Unknown requested action '$actionName' in bundle.")
            requestedActions.add(parsed)
        }

        val artifacts = try {
            obj.array("artifacts").map { item ->
                val aObj = item as? JsonValue.Obj
                    ?: return BundleImportResult.Rejected("Artifact entry must be a JSON object.")
                BundleArtifactEntry.fromJson(aObj)
            }
        } catch (ex: IllegalArgumentException) {
            return BundleImportResult.Rejected(ex.message ?: "Invalid artifact metadata.")
        }

        val bundleId = obj.stringOrDefault("bundleId")
        val jobId = obj.stringOrDefault("jobId")
        val title = obj.stringOrDefault("title")
        val goal = obj.stringOrDefault("goal")
        val maxSteps = obj.int("maxSteps", 0)
        val maxModelCalls = obj.int("maxModelCalls", -1)
        val maxRuntimeMinutes = obj.int("maxRuntimeMinutes", 0)
        val createdAt = obj.long("createdAtEpochMs", -1L)
        val claimedHash = obj.stringOrDefault("manifestSha256").lowercase()

        if (bundleId.isBlank() || jobId.isBlank() || title.isBlank() || goal.isBlank() ||
            maxSteps !in 1..100 || maxModelCalls !in 0..24 || maxRuntimeMinutes !in 1..60 || createdAt < 0L
        ) {
            return BundleImportResult.Rejected("Bundle fields out of allowed bounds.")
        }

        val expectedCanonical = SharedJobBundleManifest.canonicalPayloadJson(
            schemaVersion = version,
            bundleId = bundleId,
            jobId = jobId,
            title = title,
            goal = goal,
            profile = profile,
            sourceRole = sourceRole,
            targetRole = targetRole,
            maxSteps = maxSteps,
            maxModelCalls = maxModelCalls,
            maxRuntimeMinutes = maxRuntimeMinutes,
            requestedActions = requestedActions,
            artifacts = artifacts,
            createdAtEpochMs = createdAt,
        )
        val actualHash = ScopedWorkspaceFileManager.sha256Hex(expectedCanonical.toByteArray(Charsets.UTF_8))
        if (actualHash != claimedHash) {
            return BundleImportResult.Rejected("Bundle manifest SHA-256 mismatch (tampered or corrupted).")
        }

        // Verify supplied artifact hashes if provided
        for (art in artifacts) {
            val suppliedHash = suppliedArtifactHashesByName[art.fileName]
            if (suppliedHash != null && suppliedHash.lowercase() != art.sha256Hex) {
                return BundleImportResult.Rejected("Artifact '${art.fileName}' failed SHA-256 verification.")
            }
        }

        // Policy revalidation: Hash != Authority! Intersect requested actions with local contract.
        val allowedSet = localScopeContract?.allowedActions ?: emptySet()
        val permitted = requestedActions.filterTo(linkedSetOf()) { it in allowedSet }
        val denied = requestedActions.filterTo(linkedSetOf()) { it !in allowedSet }

        val manifest = SharedJobBundleManifest(
            schemaVersion = version,
            bundleId = bundleId,
            jobId = jobId,
            title = title,
            goal = goal,
            profile = profile,
            sourceRole = sourceRole,
            targetRole = targetRole,
            maxSteps = maxSteps,
            maxModelCalls = maxModelCalls,
            maxRuntimeMinutes = maxRuntimeMinutes,
            requestedActions = requestedActions,
            artifacts = artifacts,
            createdAtEpochMs = createdAt,
            manifestSha256 = actualHash,
        )

        return BundleImportResult.VerifiedAndRevalidated(
            RevalidatedJobBundle(
                manifest = manifest,
                locallyPermittedActions = permitted,
                deniedRequestedActions = denied,
                // External or unapproved bundle flags NEVER bypass local approval!
                requiresFreshOwnerApproval = true,
            ),
        )
    }

    companion object {
        private const val MAX_BUNDLE_JSON_CHARS = 262_144

        fun defaultModelCallCap(profile: JobProfile): Int = when (profile) {
            JobProfile.RESEARCH_SYNTHESIS -> 8
            JobProfile.CODE_COMPANION, JobProfile.PHONE_TASK_ASSISTANT -> 16
            JobProfile.COMPETITION_SOLVER -> 24
        }

        internal fun containsForbiddenSecretMarkers(text: String): Boolean {
            val checks = listOf(
                "github_pat_",
                "ghp_",
                "KGAT_",
                "COLAB_KERNEL_PROXY",
                "COLAB_TENSORBOARD",
                "Bearer ",
            )
            return checks.any { text.contains(it) }
        }
    }
}

// --- Division 30: Colab Built-In AI Adapter Boundary (Android Companion Side) ---

enum class ColabBuiltInAiState(val label: String) {
    UNPROBED("Unprobed (Manual Colab Handoff)"),
    VERIFIED_VIA_COLAB_RECEIPT("Verified Colab Built-in AI Receipt"),
    UNAVAILABLE_IN_CURRENT_RUNTIME("Unavailable Outside Colab Runtime"),
    QUOTA_EXHAUSTED("Colab Built-in AI Quota Exhausted"),
}

data class ColabAiProbeReceipt(
    val selectedModelId: String,
    val runtimeRole: BundleExecutionRole,
    val modelCallsUsed: Int,
    val maxModelCalls: Int,
    val boundedResponseText: String,
    val receiptSha256: String,
    val recordedAtEpochMs: Long,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        linkedMapOf(
            "provider" to JsonValue.Str(REQUIRED_PROVIDER_ID),
            "selectedModelId" to JsonValue.Str(selectedModelId),
            "runtimeRole" to JsonValue.Str(runtimeRole.name),
            "modelCallsUsed" to JsonValue.Num(modelCallsUsed.toDouble(), modelCallsUsed.toString()),
            "maxModelCalls" to JsonValue.Num(maxModelCalls.toDouble(), maxModelCalls.toString()),
            "boundedResponseText" to JsonValue.Str(boundedResponseText),
            "receiptSha256" to JsonValue.Str(receiptSha256),
            "recordedAtEpochMs" to JsonValue.Num(recordedAtEpochMs.toDouble(), recordedAtEpochMs.toString()),
        ),
    )

    companion object {
        const val REQUIRED_PROVIDER_ID = "google.colab.ai"
    }
}

sealed interface ColabReceiptImportOutcome {
    data class Accepted(val receipt: ColabAiProbeReceipt) : ColabReceiptImportOutcome
    data class Rejected(val reason: String) : ColabReceiptImportOutcome
}

class ColabBuiltInAiBridge(private val database: FourCBOn2Database) {
    fun currentState(): ColabBuiltInAiState {
        val raw = database.getSetting(KEY_COLAB_AI_STATE) ?: return ColabBuiltInAiState.UNPROBED
        return runCatching { ColabBuiltInAiState.valueOf(raw) }.getOrDefault(ColabBuiltInAiState.UNPROBED)
    }

    /**
     * Imports a bounded `google.colab.ai` probe/execution receipt produced by `4CBOn2_Kaggle6.ipynb`.
     * Rejects alternate providers (no silent OpenAI/Gemini-key fallback) and rejects any receipt
     * containing Colab proxy tokens or exceeding bounded prompt/response caps.
     */
    fun importVerifiedColabReceipt(
        rawJson: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ColabReceiptImportOutcome {
        if (SharedJobBundleManager.containsForbiddenSecretMarkers(rawJson)) {
            return ColabReceiptImportOutcome.Rejected("Receipt rejected: contains proxy credential or token marker.")
        }
        val obj = try {
            JsonCodec.parseObject(rawJson)
        } catch (ex: JsonParseException) {
            return ColabReceiptImportOutcome.Rejected("Malformed Colab receipt JSON: ${ex.message}")
        }

        val provider = obj.stringOrDefault("provider")
        if (provider != ColabAiProbeReceipt.REQUIRED_PROVIDER_ID) {
            return ColabReceiptImportOutcome.Rejected(
                "Alternate provider '$provider' rejected; only '${ColabAiProbeReceipt.REQUIRED_PROVIDER_ID}' is permitted.",
            )
        }

        val runtimeRole = runCatching { BundleExecutionRole.valueOf(obj.stringOrDefault("runtimeRole")) }.getOrNull()
        if (runtimeRole != BundleExecutionRole.COLAB_CONTROLLER) {
            database.putSetting(KEY_COLAB_AI_STATE, ColabBuiltInAiState.UNAVAILABLE_IN_CURRENT_RUNTIME.name, nowEpochMs)
            return ColabReceiptImportOutcome.Rejected(
                "Live google.colab.ai requires COLAB_CONTROLLER runtime (got '$runtimeRole').",
            )
        }

        val modelId = obj.stringOrDefault("selectedModelId").trim()
        val used = obj.int("modelCallsUsed", -1)
        val cap = obj.int("maxModelCalls", -1)
        val text = obj.stringOrDefault("boundedResponseText")
        if (modelId.isEmpty() || used < 1 || cap < 1 || used > cap || text.length > MAX_RESPONSE_CHARS) {
            return ColabReceiptImportOutcome.Rejected("Colab AI receipt exceeded call or character bounds.")
        }

        val canonical = "$provider|$modelId|${runtimeRole.name}|$used|$cap|$text"
        val expectedHash = ScopedWorkspaceFileManager.sha256Hex(canonical.toByteArray(Charsets.UTF_8))
        val claimedHash = obj.stringOrDefault("receiptSha256").lowercase()
        if (claimedHash != expectedHash) {
            return ColabReceiptImportOutcome.Rejected("Colab AI receipt SHA-256 verification failed.")
        }

        val receipt = ColabAiProbeReceipt(
            selectedModelId = modelId,
            runtimeRole = runtimeRole,
            modelCallsUsed = used,
            maxModelCalls = cap,
            boundedResponseText = ConsentedObservationBoundary.redactSecrets(text),
            receiptSha256 = expectedHash,
            recordedAtEpochMs = nowEpochMs,
        )
        database.backend.upsert(
            namespace = NS_COLAB_RECEIPTS,
            id = "latest",
            payloadJson = JsonCodec.stringify(receipt.toJson()),
            updatedAtEpochMs = nowEpochMs,
        )
        database.putSetting(KEY_COLAB_AI_STATE, ColabBuiltInAiState.VERIFIED_VIA_COLAB_RECEIPT.name, nowEpochMs)
        return ColabReceiptImportOutcome.Accepted(receipt)
    }

    companion object {
        const val NS_COLAB_RECEIPTS = "colab_ai_receipts"
        private const val KEY_COLAB_AI_STATE = "colab_ai_state_v1"
        const val MAX_RESPONSE_CHARS = 8_000
    }
}
