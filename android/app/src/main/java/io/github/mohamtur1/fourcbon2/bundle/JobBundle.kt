package io.github.mohamtur1.fourcbon2.bundle

import java.io.File
import java.security.MessageDigest

/**
 * Shared job bundle (Division 28).
 *
 * A versioned bundle with a manifest of files and SHA-256 hashes. Import
 * verifies hashes and revalidates the policy contract. **A hash is not
 * authority** — a valid bundle is only accepted when its policy contract
 * matches the current allowed scope.
 */
class JobBundle {
    data class Manifest(
        val bundleVersion: Int,
        val contractVersion: String,
        val files: List<FileEntry>,
    )

    data class FileEntry(
        val name: String,
        val sha256: String,
        val sizeBytes: Long,
    )

    companion object {
        const val CURRENT_BUNDLE_VERSION = 1

        fun sha256(content: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
            return digest.joinToString("") { byte -> byte.toInt().and(0xff).toString(16).padStart(2, '0') }
        }

        fun export(workspaceDir: File, contractVersion: String, allowedDestinations: List<String>): Manifest {
            val files = workspaceDir.listFiles()
                ?.filter { it.isFile }
                ?.sortedBy { it.name }
                ?.map { file ->
                    FileEntry(
                        name = file.name,
                        sha256 = sha256(file.readText()),
                        sizeBytes = file.length(),
                    )
                }
                ?: emptyList()
            return Manifest(CURRENT_BUNDLE_VERSION, contractVersion, files)
        }

        /**
         * Import a bundle: verify file hashes and revalidate the policy
         * contract. A hash mismatch or a contract mismatch rejects the bundle.
         */
        fun import(
            workspaceDir: File,
            manifest: Manifest,
            expectedContractVersion: String,
            allowedDestinations: List<String>,
        ): ImportResult {
            if (manifest.bundleVersion != CURRENT_BUNDLE_VERSION) {
                return ImportResult(false, "Unsupported bundle version.")
            }
            if (manifest.contractVersion != expectedContractVersion) {
                return ImportResult(false, "Contract version mismatch.")
            }
            if (manifest.files.any { it.name !in allowedDestinations }) {
                return ImportResult(false, "Bundle contains files outside the allowed scope.")
            }
            for (entry in manifest.files) {
                val file = workspaceDir.resolve(entry.name)
                if (!file.exists()) {
                    return ImportResult(false, "Missing bundle file: ${entry.name}")
                }
                if (sha256(file.readText()) != entry.sha256) {
                    return ImportResult(false, "Hash mismatch for ${entry.name}.")
                }
            }
            return ImportResult(true, "Bundle verified and accepted.")
        }
    }

    data class ImportResult(
        val accepted: Boolean,
        val reason: String,
    )
}
