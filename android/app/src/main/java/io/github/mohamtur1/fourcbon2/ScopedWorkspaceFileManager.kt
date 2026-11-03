package io.github.mohamtur1.fourcbon2

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Division 16: Safe user-selected file import and workspace export without broad phone
 * filesystem access (`READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` are never requested).
 *
 * Copies only explicitly user-picked streams into an isolated app-private workspace subfolder,
 * enforces a strict byte cap, sanitizes filenames against path traversal, and verifies SHA-256
 * hashes on both import and read/export.
 */
data class ScopedWorkspaceFileRecord(
    val id: String,
    val displayName: String,
    val storedFileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256Hex: String,
    val importedAtEpochMs: Long,
) {
    fun toJson(): JsonValue.Obj = JsonValue.Obj(
        mapOf(
            "id" to JsonValue.Str(id),
            "displayName" to JsonValue.Str(displayName),
            "storedFileName" to JsonValue.Str(storedFileName),
            "mimeType" to JsonValue.Str(mimeType),
            "sizeBytes" to JsonValue.Num(sizeBytes.toDouble(), sizeBytes.toString()),
            "sha256Hex" to JsonValue.Str(sha256Hex),
            "importedAtEpochMs" to JsonValue.Num(importedAtEpochMs.toDouble(), importedAtEpochMs.toString()),
        ),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): ScopedWorkspaceFileRecord = ScopedWorkspaceFileRecord(
            id = obj.stringOrDefault("id"),
            displayName = obj.stringOrDefault("displayName", "document"),
            storedFileName = obj.stringOrDefault("storedFileName"),
            mimeType = obj.stringOrDefault("mimeType", "application/octet-stream"),
            sizeBytes = obj.long("sizeBytes", 0L),
            sha256Hex = obj.stringOrDefault("sha256Hex"),
            importedAtEpochMs = obj.long("importedAtEpochMs", 0L),
        )
    }
}

sealed interface FileImportOutcome {
    data class Imported(val record: ScopedWorkspaceFileRecord) : FileImportOutcome
    data object EmptyFileRejected : FileImportOutcome
    data class ExceededSizeLimit(val maxBytes: Long) : FileImportOutcome
    data class IoFailure(val reason: String) : FileImportOutcome
}

sealed interface FileExportOutcome {
    data class Exported(val bytesWritten: Long, val sha256Hex: String) : FileExportOutcome
    data object FileNotFound : FileExportOutcome
    data object IntegrityCheckFailed : FileExportOutcome
    data class IoFailure(val reason: String) : FileExportOutcome
}

class ScopedWorkspaceFileManager(
    workspaceRoot: File,
    private val database: FourCBOn2Database,
    val maxImportBytes: Long = DEFAULT_MAX_IMPORT_BYTES,
) {
    private val importsDir = File(workspaceRoot, "scoped_imports").apply { mkdirs() }
    private val canonicalImportsDir = importsDir.canonicalFile

    fun importStream(
        displayName: String,
        mimeType: String = "application/octet-stream",
        input: InputStream,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): FileImportOutcome {
        val cleanDisplayName = sanitizeFileName(displayName)
        val id = "file_${UUID.randomUUID().toString().take(8)}"
        val storedFileName = "${id}_$cleanDisplayName"
        val targetFile = resolveInsideWorkspace(storedFileName)
            ?: return FileImportOutcome.IoFailure("Rejected unsafe file path.")
        val tempFile = File(importsDir, "$storedFileName.tmp")

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            FileOutputStream(tempFile).use { out ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    totalBytes += read
                    if (totalBytes > maxImportBytes) {
                        out.close()
                        tempFile.delete()
                        return FileImportOutcome.ExceededSizeLimit(maxImportBytes)
                    }
                    digest.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                }
            }

            if (totalBytes == 0L) {
                tempFile.delete()
                return FileImportOutcome.EmptyFileRejected
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            val sha256Hex = digest.digest().toHexStringLower()
            val record = ScopedWorkspaceFileRecord(
                id = id,
                displayName = cleanDisplayName,
                storedFileName = storedFileName,
                mimeType = mimeType.ifBlank { "application/octet-stream" },
                sizeBytes = totalBytes,
                sha256Hex = sha256Hex,
                importedAtEpochMs = nowEpochMs,
            )
            database.backend.upsert(
                namespace = FourCBOn2Database.NS_WORKSPACE_FILES,
                id = record.id,
                payloadJson = JsonCodec.stringify(record.toJson()),
                updatedAtEpochMs = nowEpochMs,
            )
            FileImportOutcome.Imported(record)
        } catch (ex: IOException) {
            tempFile.delete()
            FileImportOutcome.IoFailure(ex.message ?: "Failed to copy selected document.")
        }
    }

    fun importUtf8Text(
        displayName: String,
        text: String,
        mimeType: String = "text/plain",
        nowEpochMs: Long = System.currentTimeMillis(),
    ): FileImportOutcome =
        ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)).use { stream ->
            importStream(displayName = displayName, mimeType = mimeType, input = stream, nowEpochMs = nowEpochMs)
        }

    fun listFiles(): List<ScopedWorkspaceFileRecord> =
        database.backend.listByNamespace(FourCBOn2Database.NS_WORKSPACE_FILES)
            .mapNotNull { row ->
                runCatching { ScopedWorkspaceFileRecord.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
            }

    fun getRecord(fileId: String): ScopedWorkspaceFileRecord? {
        val row = database.backend.get(FourCBOn2Database.NS_WORKSPACE_FILES, fileId) ?: return null
        return runCatching { ScopedWorkspaceFileRecord.fromJson(JsonCodec.parseObject(row.payloadJson)) }.getOrNull()
    }

    /**
     * Reads verified bytes from the isolated workspace directory; returns null if missing or if
     * the on-disk SHA-256 no longer matches the recorded import hash.
     */
    fun readVerifiedBytes(fileId: String): ByteArray? {
        val record = getRecord(fileId) ?: return null
        val file = resolveInsideWorkspace(record.storedFileName) ?: return null
        if (!file.exists()) return null
        val bytes = try {
            file.readBytes()
        } catch (_: IOException) {
            return null
        }
        val actualHash = sha256Hex(bytes)
        return if (actualHash == record.sha256Hex) bytes else null
    }

    fun readVerifiedUtf8Text(fileId: String): String? =
        readVerifiedBytes(fileId)?.toString(Charsets.UTF_8)

    fun exportFile(fileId: String, output: OutputStream): FileExportOutcome {
        val record = getRecord(fileId) ?: return FileExportOutcome.FileNotFound
        val file = resolveInsideWorkspace(record.storedFileName) ?: return FileExportOutcome.FileNotFound
        if (!file.exists()) return FileExportOutcome.FileNotFound

        return try {
            val bytes = FileInputStream(file).use { it.readBytes() }
            val actualHash = sha256Hex(bytes)
            if (actualHash != record.sha256Hex) {
                return FileExportOutcome.IntegrityCheckFailed
            }
            output.write(bytes)
            output.flush()
            FileExportOutcome.Exported(bytesWritten = bytes.size.toLong(), sha256Hex = actualHash)
        } catch (ex: IOException) {
            FileExportOutcome.IoFailure(ex.message ?: "Failed to export file.")
        }
    }

    fun exportGeneratedBytes(payload: ByteArray, output: OutputStream): FileExportOutcome = try {
        val hash = sha256Hex(payload)
        output.write(payload)
        output.flush()
        FileExportOutcome.Exported(bytesWritten = payload.size.toLong(), sha256Hex = hash)
    } catch (ex: IOException) {
        FileExportOutcome.IoFailure(ex.message ?: "Failed to export workspace artifact.")
    }

    fun deleteFile(fileId: String): Boolean {
        val record = getRecord(fileId) ?: return false
        resolveInsideWorkspace(record.storedFileName)?.delete()
        return database.backend.delete(FourCBOn2Database.NS_WORKSPACE_FILES, fileId)
    }

    /** Android SAF helper: imports a single user-picked [Uri] via [Context.getContentResolver]. */
    fun importFromSafUri(context: Context, uri: Uri, nowEpochMs: Long = System.currentTimeMillis()): FileImportOutcome {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val displayName = queryDisplayName(context, uri) ?: "selected_document"
        val stream = try {
            resolver.openInputStream(uri)
        } catch (ex: IOException) {
            return FileImportOutcome.IoFailure(ex.message ?: "Could not open selected document.")
        } ?: return FileImportOutcome.IoFailure("Selected document provider returned no stream.")

        return stream.use { input ->
            importStream(displayName = displayName, mimeType = mimeType, input = input, nowEpochMs = nowEpochMs)
        }
    }

    /** Android SAF helper: exports a verified workspace file to a user-chosen destination [Uri]. */
    fun exportToSafUri(context: Context, fileId: String, destinationUri: Uri): FileExportOutcome {
        val stream = try {
            context.contentResolver.openOutputStream(destinationUri, "wt")
        } catch (ex: IOException) {
            return FileExportOutcome.IoFailure(ex.message ?: "Could not open export destination.")
        } ?: return FileExportOutcome.IoFailure("Export destination returned no stream.")

        return stream.use { out -> exportFile(fileId, out) }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun resolveInsideWorkspace(fileName: String): File? {
        val candidate = File(importsDir, fileName).canonicalFile
        val parentPath = canonicalImportsDir.path + File.separator
        return if (candidate.path.startsWith(parentPath)) candidate else null
    }

    companion object {
        const val DEFAULT_MAX_IMPORT_BYTES: Long = 10L * 1024L * 1024L // 10 MiB bounded import

        fun sanitizeFileName(rawName: String): String {
            val leaf = rawName
                .replace('\\', '/')
                .substringAfterLast('/')
                .replace("\u0000", "")
                .trim()
            val cleaned = leaf
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .trimStart('.')
                .take(80)
            return cleaned.ifEmpty { "document.bin" }
        }

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).toHexStringLower()
    }
}

internal fun ByteArray.toHexStringLower(): String {
    val chars = CharArray(size * 2)
    val digits = "0123456789abcdef"
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        chars[i * 2] = digits[v ushr 4]
        chars[i * 2 + 1] = digits[v and 0x0F]
    }
    return String(chars)
}
