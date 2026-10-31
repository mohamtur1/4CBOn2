package io.github.mohamtur1.fourcbon2.knowledge

import io.github.mohamtur1.fourcbon2.local.KnowledgeEntry

/**
 * Offline knowledge library (Division 17).
 *
 * Read and search saved reports and evidence with provenance and update
 * times. Search results are ranked by simple term relevance and always
 * show the source and update time so stale content is visible.
 */
class KnowledgeLibrary(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val entries = mutableListOf<KnowledgeEntry>()

    fun save(entry: KnowledgeEntry): KnowledgeEntry {
        require(entry.title.isNotBlank()) { "Title is required." }
        require(entry.source.isNotBlank()) { "Source is required." }
        entries.removeAll { it.id == entry.id }
        entries.add(entry.copy(updatedAtEpochMs = entry.updatedAtEpochMs.takeIf { it > 0 } ?: clock()))
        return entry
    }

    fun read(id: String): KnowledgeEntry? = entries.find { it.id == id }

    fun all(): List<KnowledgeEntry> = entries.toList()

    /**
     * Search with provenance. Results carry their source and update time so
     * the caller can display freshness honestly.
     */
    fun search(query: String): List<KnowledgeEntry> {
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return entries.map { entry ->
            val haystack = (entry.title + " " + entry.content).lowercase()
            val score = terms.count { haystack.contains(it) }
            entry to score
        }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun updateTime(id: String): Long? = read(id)?.updatedAtEpochMs
}
