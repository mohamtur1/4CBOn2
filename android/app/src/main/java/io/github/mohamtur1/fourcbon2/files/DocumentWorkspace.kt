package io.github.mohamtur1.fourcbon2.files

import java.io.File

/**
 * Safe file import/export workspace (Division 16).
 *
 * Only files inside the app's own workspace directory can be imported or
 * exported. Broad phone filesystem access is never granted; imports outside
 * the workspace root are rejected.
 */
class DocumentWorkspace(
    private val workspaceRoot: File,
) {
    init {
        require(workspaceRoot.exists() && workspaceRoot.isDirectory) {
            "Workspace root must exist: ${workspaceRoot.path}"
        }
    }

    fun resolve(name: String): File {
        require(name.isNotBlank()) { "File name is required." }
        require(!name.contains("..")) { "Path traversal is not allowed." }
        val resolved = workspaceRoot.resolve(name).normalize()
        require(resolved.startsWith(workspaceRoot.normalize())) {
            "Resolved path escapes the workspace: $name"
        }
        return resolved
    }

    fun importText(name: String, content: String): File {
        val file = resolve(name)
        require(!file.exists()) { "File already exists: $name" }
        file.writeText(content)
        return file
    }

    fun readText(name: String): String = resolve(name).readText()

    fun exportText(name: String): String {
        val file = resolve(name)
        require(file.exists()) { "File does not exist: $name" }
        return file.readText()
    }

    fun listWorkspaceFiles(): List<String> =
        workspaceRoot.listFiles()
            ?.filter { it.isFile }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
}
