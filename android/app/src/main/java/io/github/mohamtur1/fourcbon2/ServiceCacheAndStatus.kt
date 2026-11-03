package io.github.mohamtur1.fourcbon2

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Divisions 22 & 23:
 * - Division 22: GitHub cache and diff — refresh permitted repository file snapshots, compute
 *   deterministic unified local diffs, and prepare exportable proposed changes without performing
 *   any unapproved remote write or force-push.
 * - Division 23: Kaggle metadata/status — competition and job status views with honest last-sync
 *   timestamps, staleness computation, and auth/offline error states.
 */
data class CachedRepoFileSnapshot(
    val repoPath: String,
    val baseContent: String,
    val baseSha256: String,
    val localWorkingContent: String,
    val syncedAtEpochMs: Long,
    val editedAtEpochMs: Long,
) {
    val hasLocalChanges: Boolean
        get() = baseContent != localWorkingContent

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        mapOf(
            "repoPath" to JsonValue.Str(repoPath),
            "baseContent" to JsonValue.Str(baseContent),
            "baseSha256" to JsonValue.Str(baseSha256),
            "localWorkingContent" to JsonValue.Str(localWorkingContent),
            "syncedAtEpochMs" to JsonValue.Num(syncedAtEpochMs.toDouble(), syncedAtEpochMs.toString()),
            "editedAtEpochMs" to JsonValue.Num(editedAtEpochMs.toDouble(), editedAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): CachedRepoFileSnapshot {
            val base = obj.stringOrDefault("baseContent")
            return CachedRepoFileSnapshot(
                repoPath = obj.stringOrDefault("repoPath"),
                baseContent = base,
                baseSha256 = obj.string("baseSha256")
                    ?: ScopedWorkspaceFileManager.sha256Hex(base.toByteArray(Charsets.UTF_8)),
                localWorkingContent = obj.stringOrDefault("localWorkingContent", base),
                syncedAtEpochMs = obj.long("syncedAtEpochMs", 0L),
                editedAtEpochMs = obj.long("editedAtEpochMs", 0L),
            )
        }
    }
}

data class ExportableRepoPatch(
    val repository: String,
    val branch: String,
    val repoPath: String,
    val baseSha256: String,
    val proposedSha256: String,
    val addedLines: Int,
    val removedLines: Int,
    val unifiedDiff: String,
    val preparedAtEpochMs: Long,
) {
    fun toExportJson(): String = JsonCodec.stringify(
        JsonValue.Obj(
            mapOf(
                "repository" to JsonValue.Str(repository),
                "branch" to JsonValue.Str(branch),
                "repoPath" to JsonValue.Str(repoPath),
                "baseSha256" to JsonValue.Str(baseSha256),
                "proposedSha256" to JsonValue.Str(proposedSha256),
                "addedLines" to JsonValue.Num(addedLines.toDouble(), addedLines.toString()),
                "removedLines" to JsonValue.Num(removedLines.toDouble(), removedLines.toString()),
                "unifiedDiff" to JsonValue.Str(unifiedDiff),
                "preparedAtEpochMs" to JsonValue.Num(preparedAtEpochMs.toDouble(), preparedAtEpochMs.toString()),
                "remoteWritePerformed" to JsonValue.Bool(false),
            ),
        ),
    )
}

sealed interface GitHubSnapshotRefreshOutcome {
    data class Refreshed(val snapshot: CachedRepoFileSnapshot) : GitHubSnapshotRefreshOutcome
    data class RejectedPath(val reason: String) : GitHubSnapshotRefreshOutcome
    data class HttpError(val statusCode: Int) : GitHubSnapshotRefreshOutcome
    data object NetworkUnavailable : GitHubSnapshotRefreshOutcome
}

class GitHubCacheAndDiffManager(
    private val database: FourCBOn2Database,
    private val credentialStore: CredentialStore,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) {
    fun refreshPermittedFile(
        repoPath: String,
        preserveUnsavedDraft: Boolean = true,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): GitHubSnapshotRefreshOutcome {
        val cleanPath = sanitizeRepoPath(repoPath)
            ?: return GitHubSnapshotRefreshOutcome.RejectedPath("Invalid or disallowed repository path: $repoPath")

        val rawUrl = URL("https://raw.githubusercontent.com/${ColabNotebookReference.repositoryOwner}/${ColabNotebookReference.repositoryName}/${ColabNotebookReference.branch}/$cleanPath")
        val pat = credentialStore.load(CredentialSlot.GITHUB_FINE_GRAINED_PAT)
        val fetchedText: String
        try {
            val conn = connectionFactory(rawUrl)
            try {
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.useCaches = false
                if (pat != null) {
                    conn.setRequestProperty("Authorization", "Bearer ${pat.concatToString()}")
                }
                val code = conn.responseCode
                if (code != 200) {
                    return GitHubSnapshotRefreshOutcome.HttpError(code)
                }
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.size > MAX_CACHED_FILE_BYTES) {
                    return GitHubSnapshotRefreshOutcome.RejectedPath("File exceeds 1 MiB local cache cap.")
                }
                fetchedText = bytes.toString(Charsets.UTF_8)
            } finally {
                conn.disconnect()
            }
        } catch (_: IOException) {
            return GitHubSnapshotRefreshOutcome.NetworkUnavailable
        } finally {
            pat?.fill('\u0000')
        }

        val existing = getSnapshot(cleanPath)
        val workingContent = if (preserveUnsavedDraft && existing != null && existing.hasLocalChanges) {
            existing.localWorkingContent
        } else {
            fetchedText
        }
        val snapshot = CachedRepoFileSnapshot(
            repoPath = cleanPath,
            baseContent = fetchedText,
            baseSha256 = ScopedWorkspaceFileManager.sha256Hex(fetchedText.toByteArray(Charsets.UTF_8)),
            localWorkingContent = workingContent,
            syncedAtEpochMs = nowEpochMs,
            editedAtEpochMs = if (workingContent != fetchedText) existing?.editedAtEpochMs ?: nowEpochMs else nowEpochMs,
        )
        return GitHubSnapshotRefreshOutcome.Refreshed(saveSnapshot(snapshot, nowEpochMs))
    }

    fun cacheOfflineSnapshot(
        repoPath: String,
        baseContent: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CachedRepoFileSnapshot {
        val cleanPath = sanitizeRepoPath(repoPath)
            ?: throw IllegalArgumentException("Invalid repository path: $repoPath")
        val snapshot = CachedRepoFileSnapshot(
            repoPath = cleanPath,
            baseContent = baseContent,
            baseSha256 = ScopedWorkspaceFileManager.sha256Hex(baseContent.toByteArray(Charsets.UTF_8)),
            localWorkingContent = baseContent,
            syncedAtEpochMs = nowEpochMs,
            editedAtEpochMs = nowEpochMs,
        )
        return saveSnapshot(snapshot, nowEpochMs)
    }

    fun updateLocalWorkingCopy(
        repoPath: String,
        newContent: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): CachedRepoFileSnapshot? {
        val existing = getSnapshot(repoPath) ?: return null
        return saveSnapshot(
            existing.copy(localWorkingContent = newContent, editedAtEpochMs = nowEpochMs),
            nowEpochMs = nowEpochMs,
        )
    }

    fun getSnapshot(repoPath: String): CachedRepoFileSnapshot? {
        val clean = sanitizeRepoPath(repoPath) ?: return null
        val row = database.backend.get(NS_GITHUB_CACHE, clean) ?: return null
        return runCatching { CachedRepoFileSnapshot.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun listSnapshots(): List<CachedRepoFileSnapshot> =
        database.backend.listByNamespace(NS_GITHUB_CACHE)
            .mapNotNull { row ->
                runCatching { CachedRepoFileSnapshot.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }

    /**
     * Prepares a deterministic unified diff and exportable patch manifest locally.
     * Never pushes, commits, or mutates remote GitHub state.
     */
    fun prepareExportableChange(
        repoPath: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ExportableRepoPatch? {
        val snap = getSnapshot(repoPath) ?: return null
        val diffResult = computeUnifiedDiff(
            filePath = snap.repoPath,
            original = snap.baseContent,
            modified = snap.localWorkingContent,
        )
        return ExportableRepoPatch(
            repository = "${ColabNotebookReference.repositoryOwner}/${ColabNotebookReference.repositoryName}",
            branch = ColabNotebookReference.branch,
            repoPath = snap.repoPath,
            baseSha256 = snap.baseSha256,
            proposedSha256 = ScopedWorkspaceFileManager.sha256Hex(
                snap.localWorkingContent.toByteArray(Charsets.UTF_8),
            ),
            addedLines = diffResult.addedCount,
            removedLines = diffResult.removedCount,
            unifiedDiff = diffResult.diffText,
            preparedAtEpochMs = nowEpochMs,
        )
    }

    private fun saveSnapshot(snapshot: CachedRepoFileSnapshot, nowEpochMs: Long): CachedRepoFileSnapshot {
        database.backend.upsert(
            namespace = NS_GITHUB_CACHE,
            id = snapshot.repoPath,
            payloadJson = JsonCodec.stringify(snapshot.toJson()),
            updatedAtEpochMs = nowEpochMs,
        )
        return snapshot
    }

    internal data class DiffStats(val diffText: String, val addedCount: Int, val removedCount: Int)

    companion object {
        const val NS_GITHUB_CACHE = "github_file_cache"
        private const val MAX_CACHED_FILE_BYTES = 1_048_576

        fun sanitizeRepoPath(rawPath: String): String? {
            val trimmed = rawPath.trim().trimStart('/')
            if (trimmed.isEmpty() || trimmed.contains("..") || trimmed.contains('\\') || trimmed.contains('\u0000')) {
                return null
            }
            val segments = trimmed.split('/')
            if (segments.any { it.isEmpty() || it == "." || it == ".git" }) return null
            return trimmed
        }

        internal fun computeUnifiedDiff(
            filePath: String,
            original: String,
            modified: String,
        ): DiffStats {
            if (original == modified) {
                return DiffStats(
                    diffText = "--- a/$filePath\n+++ b/$filePath\n(no changes)",
                    addedCount = 0,
                    removedCount = 0,
                )
            }
            val a = original.lines()
            val b = modified.lines()
            // Bounded LCS matrix for clean unified diff
            val m = a.size
            val n = b.size
            val sb = StringBuilder()
            sb.appendLine("--- a/$filePath")
            sb.appendLine("+++ b/$filePath")
            sb.appendLine("@@ -1,$m +1,$n @@")

            var added = 0
            var removed = 0

            if (m.toLong() * n.toLong() <= 250_000L) {
                val dp = Array(m + 1) { IntArray(n + 1) }
                for (i in m - 1 downTo 0) {
                    for (j in n - 1 downTo 0) {
                        dp[i][j] = if (a[i] == b[j]) 1 + dp[i + 1][j + 1] else maxOf(dp[i + 1][j], dp[i][j + 1])
                    }
                }
                var i = 0
                var j = 0
                while (i < m && j < n) {
                    if (a[i] == b[j]) {
                        sb.appendLine(" ${a[i]}")
                        i++
                        j++
                    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                        sb.appendLine("-${a[i]}")
                        removed++
                        i++
                    } else {
                        sb.appendLine("+${b[j]}")
                        added++
                        j++
                    }
                }
                while (i < m) {
                    sb.appendLine("-${a[i]}")
                    removed++
                    i++
                }
                while (j < n) {
                    sb.appendLine("+${b[j]}")
                    added++
                    j++
                }
            } else {
                for (line in a) {
                    sb.appendLine("-$line")
                    removed++
                }
                for (line in b) {
                    sb.appendLine("+$line")
                    added++
                }
            }

            return DiffStats(sb.toString().trimEnd(), added, removed)
        }
    }
}

// --- Division 23: Kaggle Metadata & Honest Sync/Stale Status ---

enum class KaggleServiceSyncState(val label: String) {
    NEVER_SYNCED("Not Configured / Never Synced"),
    CREDENTIAL_MISSING("Waiting for Local Kaggle Token"),
    VERIFIED_ONLINE("API Authorized & Synced"),
    AUTH_REJECTED("Reauthorization Needed (HTTP 401/403)"),
    RATE_LIMITED("Rate Limited (HTTP 429)"),
    OFFLINE_OR_UNREACHABLE("Offline / Network Unreachable"),
    SERVICE_ERROR("Kaggle Service Unavailable"),
}

data class TrackedKaggleCompetition(
    val slug: String,
    val title: String,
    val evaluationMetric: String,
    val submissionCapPerDay: Int,
    val lastKnownJobState: String,
    val lastSyncEpochMs: Long?,
    val lastSyncOutcome: KaggleServiceSyncState,
) {
    fun isStale(
        nowEpochMs: Long = System.currentTimeMillis(),
        staleThresholdMs: Long = DEFAULT_STALE_THRESHOLD_MS,
    ): Boolean {
        val synced = lastSyncEpochMs ?: return true
        return (nowEpochMs - synced) > staleThresholdMs ||
            lastSyncOutcome != KaggleServiceSyncState.VERIFIED_ONLINE
    }

    fun statusBadge(nowEpochMs: Long = System.currentTimeMillis()): String = when {
        lastSyncEpochMs == null -> lastSyncOutcome.label
        lastSyncOutcome != KaggleServiceSyncState.VERIFIED_ONLINE ->
            "${lastSyncOutcome.label} (cached view)"
        isStale(nowEpochMs) -> "Stale · Last synced ${(nowEpochMs - lastSyncEpochMs) / 60_000L}m ago"
        else -> "Fresh · Synced"
    }

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        mapOf(
            "slug" to JsonValue.Str(slug),
            "title" to JsonValue.Str(title),
            "evaluationMetric" to JsonValue.Str(evaluationMetric),
            "submissionCapPerDay" to JsonValue.Num(submissionCapPerDay.toDouble(), submissionCapPerDay.toString()),
            "lastKnownJobState" to JsonValue.Str(lastKnownJobState),
            "lastSyncEpochMs" to (
                lastSyncEpochMs?.let { JsonValue.Num(it.toDouble(), it.toString()) } ?: JsonValue.Null
                ),
            "lastSyncOutcome" to JsonValue.Str(lastSyncOutcome.name),
        ),
    )

    companion object {
        const val DEFAULT_STALE_THRESHOLD_MS: Long = 30L * 60L * 1000L

        fun fromJson(obj: JsonValue.Obj): TrackedKaggleCompetition = TrackedKaggleCompetition(
            slug = obj.stringOrDefault("slug", "tabular-playground"),
            title = obj.stringOrDefault("title", "Tabular Baseline Competition"),
            evaluationMetric = obj.stringOrDefault("evaluationMetric", "ROC-AUC"),
            submissionCapPerDay = obj.int("submissionCapPerDay", 5).coerceIn(1, 50),
            lastKnownJobState = obj.stringOrDefault("lastKnownJobState", "Prepare-only (not submitted)"),
            lastSyncEpochMs = obj.entries["lastSyncEpochMs"]?.let { (it as? JsonValue.Num)?.toLongOrNull() },
            lastSyncOutcome = runCatching { KaggleServiceSyncState.valueOf(obj.stringOrDefault("lastSyncOutcome")) }
                .getOrDefault(KaggleServiceSyncState.NEVER_SYNCED),
        )
    }
}

class KaggleStatusRepository(
    private val database: FourCBOn2Database,
    private val readClient: KaggleCompetitionReadClient,
) {
    fun trackCompetition(
        slug: String,
        title: String,
        evaluationMetric: String,
        submissionCapPerDay: Int = 5,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): TrackedKaggleCompetition {
        val cleanSlug = slug.trim().lowercase().replace(Regex("[^a-z0-9\\-]"), "-")
        require(cleanSlug.isNotBlank()) { "Competition slug must not be blank." }
        val existing = getCompetition(cleanSlug)
        val entry = TrackedKaggleCompetition(
            slug = cleanSlug,
            title = title.trim(),
            evaluationMetric = evaluationMetric.trim(),
            submissionCapPerDay = submissionCapPerDay.coerceIn(1, 50),
            lastKnownJobState = existing?.lastKnownJobState ?: "Prepare-only (not submitted)",
            lastSyncEpochMs = existing?.lastSyncEpochMs,
            lastSyncOutcome = existing?.lastSyncOutcome ?: KaggleServiceSyncState.NEVER_SYNCED,
        )
        return persist(entry, nowEpochMs)
    }

    /**
     * Synchronizes status using the read-only [KaggleCompetitionReadClient] and records honest
     * sync timestamps versus error/stale states without inventing online results.
     */
    fun syncAuthorizedStatus(
        slug: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): TrackedKaggleCompetition? {
        val existing = getCompetition(slug) ?: return null
        val probe = readClient.probeAuthorizedCompetitionList()
        val mappedState = when (probe) {
            KaggleReadResult.CompetitionListVisible -> KaggleServiceSyncState.VERIFIED_ONLINE
            KaggleReadResult.CredentialMissing -> KaggleServiceSyncState.CREDENTIAL_MISSING
            KaggleReadResult.AuthenticationRequired,
            KaggleReadResult.AccessForbidden,
            -> KaggleServiceSyncState.AUTH_REJECTED
            KaggleReadResult.RateLimited -> KaggleServiceSyncState.RATE_LIMITED
            KaggleReadResult.NetworkUnavailable -> KaggleServiceSyncState.OFFLINE_OR_UNREACHABLE
            KaggleReadResult.ServiceUnavailable,
            is KaggleReadResult.UnexpectedResponse,
            -> KaggleServiceSyncState.SERVICE_ERROR
        }
        val syncedTime = if (mappedState == KaggleServiceSyncState.VERIFIED_ONLINE) {
            nowEpochMs
        } else {
            existing.lastSyncEpochMs
        }
        return persist(
            existing.copy(
                lastSyncEpochMs = syncedTime,
                lastSyncOutcome = mappedState,
            ),
            nowEpochMs = nowEpochMs,
        )
    }

    fun getCompetition(slug: String): TrackedKaggleCompetition? {
        val row = database.backend.get(NS_KAGGLE_STATUS, slug) ?: return null
        return runCatching { TrackedKaggleCompetition.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun listCompetitions(): List<TrackedKaggleCompetition> =
        database.backend.listByNamespace(NS_KAGGLE_STATUS)
            .mapNotNull { row ->
                runCatching { TrackedKaggleCompetition.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }

    private fun persist(item: TrackedKaggleCompetition, nowEpochMs: Long): TrackedKaggleCompetition {
        database.backend.upsert(
            namespace = NS_KAGGLE_STATUS,
            id = item.slug,
            payloadJson = JsonCodec.stringify(item.toJson()),
            updatedAtEpochMs = nowEpochMs,
        )
        return item
    }

    companion object {
        const val NS_KAGGLE_STATUS = "kaggle_competition_status"
    }
}
