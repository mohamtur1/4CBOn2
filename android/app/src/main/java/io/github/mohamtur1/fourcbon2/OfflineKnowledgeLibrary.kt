package io.github.mohamtur1.fourcbon2

import java.util.UUID

/**
 * Division 17: Offline Knowledge Library — stores, updates, lists, and searches saved reports,
 * notes, tutorials, and local analysis outputs with provenance, SHA-256 content hashes, and
 * update timestamps. Every search result carries an explicit `Offline Library` engine label.
 */
enum class KnowledgeProvenance(val label: String) {
    USER_NOTE("User Note"),
    IMPORTED_FILE("Imported File"),
    PUBLIC_PAGE_SNAPSHOT("Public Page Snapshot"),
    COMPETITION_BRIEF("Competition Brief"),
    LOCAL_ANALYSIS("Local Routine Analysis"),
    SYSTEM_TUTORIAL("How do I? Tutorial"),
}

data class KnowledgeEntry(
    val id: String,
    val title: String,
    val body: String,
    val sourceUrl: String?,
    val provenance: KnowledgeProvenance,
    val tags: List<String>,
    val contentSha256: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    init {
        require(title.isNotBlank()) { "Knowledge entry title must not be blank." }
        require(body.isNotBlank()) { "Knowledge entry body must not be blank." }
    }

    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        mapOf(
            "id" to JsonValue.Str(id),
            "title" to JsonValue.Str(title),
            "body" to JsonValue.Str(body),
            "sourceUrl" to (sourceUrl?.let(JsonValue::Str) ?: JsonValue.Null),
            "provenance" to JsonValue.Str(provenance.name),
            "tags" to JsonValue.Arr(tags.map(JsonValue::Str)),
            "contentSha256" to JsonValue.Str(contentSha256),
            "createdAtEpochMs" to JsonValue.Num(createdAtEpochMs.toDouble(), createdAtEpochMs.toString()),
            "updatedAtEpochMs" to JsonValue.Num(updatedAtEpochMs.toDouble(), updatedAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): KnowledgeEntry {
            val body = obj.stringOrDefault("body", "Empty")
            return KnowledgeEntry(
                id = obj.stringOrDefault("id", UUID.randomUUID().toString()),
                title = obj.stringOrDefault("title", "Untitled"),
                body = body,
                sourceUrl = obj.string("sourceUrl"),
                provenance = runCatching { KnowledgeProvenance.valueOf(obj.stringOrDefault("provenance")) }
                    .getOrDefault(KnowledgeProvenance.USER_NOTE),
                tags = obj.stringList("tags"),
                contentSha256 = obj.string("contentSha256")
                    ?: ScopedWorkspaceFileManager.sha256Hex(body.toByteArray(Charsets.UTF_8)),
                createdAtEpochMs = obj.long("createdAtEpochMs", 0L),
                updatedAtEpochMs = obj.long("updatedAtEpochMs", 0L),
            )
        }
    }
}

data class KnowledgeSearchHit(
    val entry: KnowledgeEntry,
    val score: Int,
    val supportingSnippet: String,
    val engineLabel: String = ENGINE_LABEL_OFFLINE_LIBRARY,
) {
    companion object {
        const val ENGINE_LABEL_OFFLINE_LIBRARY = "Offline Library (local lookup)"
    }
}

class OfflineKnowledgeLibrary(private val database: FourCBOn2Database) {
    fun saveEntry(
        title: String,
        body: String,
        provenance: KnowledgeProvenance,
        sourceUrl: String? = null,
        tags: List<String> = emptyList(),
        existingId: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): KnowledgeEntry {
        val cleanTitle = title.trim()
        val cleanBody = body.trim()
        val existing = existingId?.let(::getEntry)
        val id = existing?.id ?: "kb_${UUID.randomUUID().toString().take(8)}"
        val created = existing?.createdAtEpochMs ?: nowEpochMs
        val entry = KnowledgeEntry(
            id = id,
            title = cleanTitle,
            body = cleanBody,
            sourceUrl = sourceUrl?.trim()?.takeIf { it.isNotEmpty() },
            provenance = provenance,
            tags = tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
            contentSha256 = ScopedWorkspaceFileManager.sha256Hex(cleanBody.toByteArray(Charsets.UTF_8)),
            createdAtEpochMs = created,
            updatedAtEpochMs = nowEpochMs,
        )
        database.backend.upsert(
            namespace = FourCBOn2Database.NS_KNOWLEDGE_LIBRARY,
            id = entry.id,
            payloadJson = JsonCodec.stringify(entry.toJson()),
            updatedAtEpochMs = entry.updatedAtEpochMs,
        )
        return entry
    }

    fun getEntry(id: String): KnowledgeEntry? {
        val row = database.backend.get(FourCBOn2Database.NS_KNOWLEDGE_LIBRARY, id) ?: return null
        return runCatching { KnowledgeEntry.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    fun listEntries(provenanceFilter: KnowledgeProvenance? = null): List<KnowledgeEntry> =
        database.backend.listByNamespace(FourCBOn2Database.NS_KNOWLEDGE_LIBRARY)
            .mapNotNull { row ->
                runCatching { KnowledgeEntry.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }
            .let { all ->
                if (provenanceFilter == null) all else all.filter { it.provenance == provenanceFilter }
            }

    fun deleteEntry(id: String): Boolean =
        database.backend.delete(FourCBOn2Database.NS_KNOWLEDGE_LIBRARY, id)

    /**
     * Deterministic keyword/phrase lookup across saved titles, tags, and bodies.
     * Never claims cloud reasoning; all hits carry [KnowledgeSearchHit.ENGINE_LABEL_OFFLINE_LIBRARY].
     */
    fun search(
        query: String,
        provenanceFilter: KnowledgeProvenance? = null,
        limit: Int = 15,
    ): List<KnowledgeSearchHit> {
        val trimmed = query.trim()
        val candidates = listEntries(provenanceFilter)
        if (trimmed.isEmpty()) {
            return candidates.take(limit).map { entry ->
                KnowledgeSearchHit(
                    entry = entry,
                    score = 1,
                    supportingSnippet = extractSnippet(entry.body, emptyList()),
                )
            }
        }

        val normalizedPhrase = trimmed.lowercase()
        val tokens = normalizedPhrase
            .split(Regex("[^a-z0-9_]+"))
            .filter { it.length >= 2 }
            .distinct()

        return candidates.mapNotNull { entry ->
            val titleLower = entry.title.lowercase()
            val bodyLower = entry.body.lowercase()
            val tagsLower = entry.tags.map { it.lowercase() }

            var score = 0
            if (titleLower.contains(normalizedPhrase)) score += 12
            if (bodyLower.contains(normalizedPhrase)) score += 6
            for (token in tokens) {
                if (titleLower.contains(token)) score += 5
                if (tagsLower.any { it.contains(token) }) score += 4
                if (bodyLower.contains(token)) score += 2
            }

            if (score <= 0) {
                null
            } else {
                KnowledgeSearchHit(
                    entry = entry,
                    score = score,
                    supportingSnippet = extractSnippet(entry.body, tokens.ifEmpty { listOf(normalizedPhrase) }),
                )
            }
        }
            .sortedWith(
                compareByDescending<KnowledgeSearchHit> { it.score }
                    .thenByDescending { it.entry.updatedAtEpochMs }
                    .thenBy { it.entry.id },
            )
            .take(limit)
    }

    /** Seeds offline "How do I?" starter tutorials once so Ask works offline immediately. */
    fun ensureStarterTutorials(nowEpochMs: Long = System.currentTimeMillis()) {
        for (seed in STARTER_TUTORIALS) {
            if (getEntry(seed.id) == null) {
                database.backend.upsert(
                    namespace = FourCBOn2Database.NS_KNOWLEDGE_LIBRARY,
                    id = seed.id,
                    payloadJson = JsonCodec.stringify(
                        seed.copy(
                            createdAtEpochMs = nowEpochMs,
                            updatedAtEpochMs = nowEpochMs,
                        ).toJson(),
                    ),
                    updatedAtEpochMs = nowEpochMs,
                )
            }
        }
    }

    private fun extractSnippet(body: String, terms: List<String>, maxChars: Int = 180): String {
        val compact = body.replace(Regex("\\s+"), " ").trim()
        if (compact.length <= maxChars) return compact
        val lower = compact.lowercase()
        val matchIdx = terms.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = (matchIdx - 40).coerceAtLeast(0)
        val end = (start + maxChars).coerceAtMost(compact.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < compact.length) "…" else ""
        return prefix + compact.substring(start, end).trim() + suffix
    }

    companion object {
        private val STARTER_TUTORIALS: List<KnowledgeEntry> = listOf(
            tutorial(
                id = "kb_tutorial_credentials",
                title = "How do I add GitHub or Kaggle credentials safely?",
                body = "Open Home -> Connections & Credentials. Choose GitHub Fine-Grained PAT, Kaggle Bearer / KGAT Token, or Kaggle API Key (kaggle.json). Secrets are encrypted locally via AndroidKeyStore AES-GCM and are never logged, exported in job bundles, or sent to untrusted pages.",
                tags = listOf("tutorial", "credentials", "kaggle", "github", "keystore"),
            ),
            tutorial(
                id = "kb_tutorial_offline_queue",
                title = "How do offline Agent Drafts and the Waiting Queue work?",
                body = "Create or customize an Agent Draft in the Agents tab offline, then tap Queue Job. Network-dependent or Colab-dependent jobs stay safely in Waiting states until internet, credentials, runtime, and owner approval are satisfied. Interrupted external jobs never auto-replay.",
                tags = listOf("tutorial", "agents", "queue", "offline", "reconnect"),
            ),
            tutorial(
                id = "kb_tutorial_files_analysis",
                title = "How do I import files and run local CSV/JSON checks?",
                body = "Use Import Document on Home to select a single CSV or JSON file via Android's system picker—no broad phone storage access is requested. Run the bounded local analyzer to inspect row counts, missing values, and numeric statistics, then save the summary to Knowledge.",
                tags = listOf("tutorial", "csv", "json", "files", "analysis"),
            ),
            tutorial(
                id = "kb_tutorial_colab_takeover",
                title = "How do Colab handoff and Take Over / Stop work?",
                body = "4CBOn2 opens 4CBOn2_Kaggle6.ipynb in Chrome Custom Tabs when interactive login or runtime startup is needed, and enforces local Take Over / Stop controls that immediately reject stale automated commands until you explicitly resume.",
                tags = listOf("tutorial", "colab", "takeover", "browser", "safety"),
            ),
        )

        private fun tutorial(id: String, title: String, body: String, tags: List<String>): KnowledgeEntry =
            KnowledgeEntry(
                id = id,
                title = title,
                body = body,
                sourceUrl = null,
                provenance = KnowledgeProvenance.SYSTEM_TUTORIAL,
                tags = tags,
                contentSha256 = ScopedWorkspaceFileManager.sha256Hex(body.toByteArray(Charsets.UTF_8)),
                createdAtEpochMs = 0L,
                updatedAtEpochMs = 0L,
            )
    }
}
