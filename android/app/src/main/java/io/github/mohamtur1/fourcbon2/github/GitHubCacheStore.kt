package io.github.mohamtur1.fourcbon2.github

import io.github.mohamtur1.fourcbon2.local.LocalDocumentStore
import java.io.File

/**
 * Local cache of permitted repository file versions (Division 22).
 *
 * Refreshes only files whose path is in the permitted set. A refresh stores
 * the fetched content and records the version/revision it came from. There is
 * no write path to the repository here: changes are prepared locally and
 * exported for owner approval only.
 */
class GitHubCacheStore(
    private val store: LocalDocumentStore,
) {
    private val files = mutableMapOf<String, CachedFile>()

    fun load() {
        files.clear()
        for (entry in store.readCollection("github-cache") { CachedFileCodec.decode(it) }) {
            files[entry.path] = entry
        }
    }

    /** Refresh one permitted file. Returns the cached entry or null if the path is not permitted. */
    fun refresh(path: String, revision: String, fetcher: (String) -> String, permittedPaths: List<String>): CachedFile? {
        require(permittedPaths.contains(path)) { "Path is not permitted for refresh: $path" }
        val content = fetcher(path)
        val entry = CachedFile(path = path, revision = revision, content = content)
        files[path] = entry
        store.writeCollection("github-cache", files.values.toList()) { CachedFileCodec.encode(it) }
        return entry
    }

    fun cached(path: String): CachedFile? = files[path]

    fun all(): List<CachedFile> = files.values.toList()

    data class CachedFile(
        val path: String,
        val revision: String,
        val content: String,
    )

    object CachedFileCodec {
        fun encode(file: CachedFile): String = io.github.mohamtur1.fourcbon2.local.RecordCodec.join(
            listOf(file.path, file.revision, file.content),
        )

        fun decode(line: String): CachedFile {
            val fields = io.github.mohamtur1.fourcbon2.local.RecordCodec.split(line)
            require(fields.size == 3) { "Invalid cache record." }
            return CachedFile(fields[0], fields[1], fields[2])
        }
    }
}
