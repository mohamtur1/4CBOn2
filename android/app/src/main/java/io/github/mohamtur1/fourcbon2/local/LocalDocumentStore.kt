package io.github.mohamtur1.fourcbon2.local

import java.io.File

/**
 * Local document store for the offline-first prototype.
 *
 * Each collection is stored as one text file inside the workspace directory
 * so data survives restarts. Dependency-free by design so the store can be
 * exercised by local JVM unit tests without Android.
 *
 * Design note: the long-term plan targets a Room data layer; this document
 * store keeps the same entity boundary so the layer can be swapped without
 * changing callers.
 */
class LocalDocumentStore(
    workspaceDir: File,
) {
    private val dir: File = workspaceDir.resolve("local-store").apply { mkdirs() }

    fun <T> readCollection(name: String, decode: (String) -> T): List<T> {
        val file = dir.resolve("$name.txt")
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }.map(decode)
    }

    fun <T> writeCollection(name: String, items: List<T>, encode: (T) -> String) {
        val file = dir.resolve("$name.txt")
        file.writeText(items.joinToString(separator = "\n") { encode(it) })
    }

    fun collectionFile(name: String): File = dir.resolve("$name.txt")
}
