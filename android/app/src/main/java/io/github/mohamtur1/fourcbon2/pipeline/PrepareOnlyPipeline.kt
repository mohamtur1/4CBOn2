package io.github.mohamtur1.fourcbon2.pipeline

import io.github.mohamtur1.fourcbon2.bundle.JobBundle
import java.io.File

/**
 * Kaggle prepare-only pipeline (Division 37).
 *
 * One trusted tabular template, explicit validation, artifact checks, and a
 * reproducible result. This pipeline never submits: submission is a separate
 * authorized gate (Division 38).
 */
class PrepareOnlyPipeline {
    data class Template(
        val name: String,
        val requiredColumns: List<String>,
    ) {
        init {
            require(name.isNotBlank()) { "Template name is required." }
            require(requiredColumns.isNotEmpty()) { "Template needs columns." }
        }
    }

    data class PrepareResult(
        val accepted: Boolean,
        val validationMessages: List<String>,
        val artifacts: List<JobBundle.FileEntry>,
    )

    /** Validate one row against the template. Returns a list of errors. */
    fun validateRow(template: Template, row: Map<String, String>): List<String> {
        val errors = mutableListOf<String>()
        for (column in template.requiredColumns) {
            if (row[column] == null || row[column]!!.isBlank()) {
                errors.add("Missing value for column '$column'.")
            }
        }
        return errors
    }

    /**
     * Prepare a trusted template: validate all rows, then check artifacts.
     * Accepted only when there are zero validation errors and every artifact
     * matches its recorded hash.
     */
    fun prepare(
        template: Template,
        rows: List<Map<String, String>>,
        artifactDir: File,
        expectedArtifacts: Map<String, String>,
    ): PrepareResult {
        val validationMessages = mutableListOf<String>()
        rows.forEachIndexed { index, row ->
            val errors = validateRow(template, row)
            if (errors.isNotEmpty()) {
                validationMessages.add("Row ${index + 1}: ${errors.joinToString("; ")}")
            }
        }
        val artifacts = artifactDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            ?.map { file ->
                JobBundle.FileEntry(
                    name = file.name,
                    sha256 = JobBundle.sha256(file.readText()),
                    sizeBytes = file.length(),
                )
            }
            ?: emptyList()
        for (entry in artifacts) {
            val expected = expectedArtifacts[entry.name]
            if (expected == null) {
                validationMessages.add("Unexpected artifact: ${entry.name}")
            } else if (expected != entry.sha256) {
                validationMessages.add("Artifact hash mismatch: ${entry.name}")
            }
        }
        for (name in expectedArtifacts.keys) {
            if (!artifacts.any { it.name == name }) {
                validationMessages.add("Missing artifact: $name")
            }
        }
        return PrepareResult(
            accepted = validationMessages.isEmpty(),
            validationMessages = validationMessages,
            artifacts = artifacts,
        )
    }
}
