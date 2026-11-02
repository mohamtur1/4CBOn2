package io.github.mohamtur1.fourcbon2

import java.io.File
import java.io.IOException

/**
 * Division 11: Durable local persistence engine supporting structured entity namespaces,
 * restart recovery, and deterministic ordering across both Android SQLite and disk-backed
 * JVM unit tests.
 */
data class StoredEntityRow(
    val namespace: String,
    val id: String,
    val payloadJson: String,
    val updatedAtEpochMs: Long,
)

interface LocalStoreBackend {
    fun upsert(namespace: String, id: String, payloadJson: String, updatedAtEpochMs: Long)
    fun get(namespace: String, id: String): StoredEntityRow?
    fun listByNamespace(namespace: String): List<StoredEntityRow>
    fun delete(namespace: String, id: String): Boolean
    fun clearNamespace(namespace: String)
}

/**
 * Atomic disk-directory store backend that persists each namespace as a structured JSON file.
 * Enables true disk-persistence and process-restart verification in JVM unit tests and serves
 * as a portable fallback when Android SQLite native libraries are absent.
 */
class DiskDirectoryBackend(private val rootDirectory: File) : LocalStoreBackend {
    private val lock = Any()

    init {
        if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
            throw IOException("Could not create local database directory: $rootDirectory")
        }
    }

    override fun upsert(namespace: String, id: String, payloadJson: String, updatedAtEpochMs: Long) {
        validateKey(namespace, "namespace")
        validateKey(id, "id")
        synchronized(lock) {
            val map = readNamespaceMapLocked(namespace)
            map[id] = StoredEntityRow(namespace, id, payloadJson, updatedAtEpochMs)
            writeNamespaceMapLocked(namespace, map)
        }
    }

    override fun get(namespace: String, id: String): StoredEntityRow? = synchronized(lock) {
        readNamespaceMapLocked(namespace)[id]
    }

    override fun listByNamespace(namespace: String): List<StoredEntityRow> = synchronized(lock) {
        readNamespaceMapLocked(namespace)
            .values
            .sortedWith(compareByDescending<StoredEntityRow> { it.updatedAtEpochMs }.thenBy { it.id })
    }

    override fun delete(namespace: String, id: String): Boolean = synchronized(lock) {
        val map = readNamespaceMapLocked(namespace)
        val removed = map.remove(id) != null
        if (removed) {
            writeNamespaceMapLocked(namespace, map)
        }
        removed
    }

    override fun clearNamespace(namespace: String) = synchronized(lock) {
        val file = namespaceFile(namespace)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun namespaceFile(namespace: String): File {
        val safeName = namespace.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return File(rootDirectory, "$safeName.table.json")
    }

    private fun readNamespaceMapLocked(namespace: String): LinkedHashMap<String, StoredEntityRow> {
        val file = namespaceFile(namespace)
        val result = LinkedHashMap<String, StoredEntityRow>()
        if (!file.exists()) return result
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (_: IOException) {
            return result
        }
        if (text.isBlank()) return result
        val root = try {
            JsonCodec.parseObject(text)
        } catch (_: JsonParseException) {
            // Corrupt table file does not crash startup; callers observe an empty/recoverable state.
            return result
        }
        for (item in root.array("rows")) {
            val obj = item as? JsonValue.Obj ?: continue
            val id = obj.string("id") ?: continue
            val payload = obj.string("payloadJson") ?: continue
            val updatedAt = obj.long("updatedAtEpochMs", 0L)
            result[id] = StoredEntityRow(namespace, id, payload, updatedAt)
        }
        return result
    }

    private fun writeNamespaceMapLocked(namespace: String, rows: Map<String, StoredEntityRow>) {
        val file = namespaceFile(namespace)
        val tempFile = File(rootDirectory, "${file.name}.tmp")
        val json = JsonCodec.stringify(
            JsonValue.Obj(
                mapOf(
                    "schemaVersion" to JsonValue.Num(1.0, "1"),
                    "namespace" to JsonValue.Str(namespace),
                    "rows" to JsonValue.Arr(
                        rows.values.map { row ->
                            JsonValue.Obj(
                                mapOf(
                                    "id" to JsonValue.Str(row.id),
                                    "payloadJson" to JsonValue.Str(row.payloadJson),
                                    "updatedAtEpochMs" to JsonValue.Num(
                                        row.updatedAtEpochMs.toDouble(),
                                        row.updatedAtEpochMs.toString(),
                                    ),
                                ),
                            )
                        },
                    ),
                ),
            ),
        )
        tempFile.writeText(json, Charsets.UTF_8)
        if (!tempFile.renameTo(file)) {
            file.writeText(json, Charsets.UTF_8)
            tempFile.delete()
        }
    }
}

/**
 * Shared application database facade backed by [LocalStoreBackend].
 */
class FourCBOn2Database(val backend: LocalStoreBackend) {
    fun putSetting(key: String, value: String, nowEpochMs: Long = System.currentTimeMillis()) {
        val payload = JsonCodec.stringify(JsonValue.Obj(mapOf("value" to JsonValue.Str(value))))
        backend.upsert(NS_SETTINGS, key, payload, nowEpochMs)
    }

    fun getSetting(key: String): String? {
        val row = backend.get(NS_SETTINGS, key) ?: return null
        return try {
            JsonCodec.parseObject(row.payloadJson).string("value")
        } catch (_: JsonParseException) {
            null
        }
    }

    companion object {
        const val NS_SETTINGS = "app_settings"
        const val NS_AGENT_DRAFTS = "agent_drafts"
        const val NS_WORKSPACE_FILES = "workspace_files"
        const val NS_KNOWLEDGE_LIBRARY = "knowledge_library"
        const val NS_WAITING_QUEUE = "waiting_queue"
    }
}

internal fun validateKey(value: String, label: String) {
    require(value.isNotBlank() && value.length <= 128) { "Invalid $label: must be 1..128 non-blank chars." }
}
