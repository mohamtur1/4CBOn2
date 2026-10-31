package io.github.mohamtur1.fourcbon2.pipeline

import io.github.mohamtur1.fourcbon2.bundle.JobBundle
import io.github.mohamtur1.fourcbon2.testutil.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrepareOnlyPipelineTest {

    private val template = PrepareOnlyPipeline.Template("trusted", listOf("id", "value"))

    @Test
    fun validRowsAndArtifactsAreAccepted() {
        val dir = createTempDirectory("pipeline")
        File(dir, "artifact.csv").writeText("id,value\n1,2\n")
        val hash = JobBundle.sha256(File(dir, "artifact.csv").readText())
        val pipeline = PrepareOnlyPipeline()
        val result = pipeline.prepare(
            template,
            rows = listOf(mapOf("id" to "1", "value" to "2")),
            artifactDir = dir,
            expectedArtifacts = mapOf("artifact.csv" to hash),
        )
        assertTrue(result.accepted)
        assertTrue(result.validationMessages.isEmpty())
    }

    @Test
    fun missingColumnValuesAreReported() {
        val dir = createTempDirectory("pipeline-invalid")
        val pipeline = PrepareOnlyPipeline()
        val result = pipeline.prepare(
            template,
            rows = listOf(mapOf("id" to "1")),
            artifactDir = dir,
            expectedArtifacts = emptyMap(),
        )
        assertFalse(result.accepted)
        assertTrue(result.validationMessages.any { it.contains("value") })
    }

    @Test
    fun tamperedArtifactIsReported() {
        val dir = createTempDirectory("pipeline-tamper")
        File(dir, "artifact.csv").writeText("a")
        val hash = JobBundle.sha256("a")
        File(dir, "artifact.csv").writeText("b")
        val pipeline = PrepareOnlyPipeline()
        val result = pipeline.prepare(
            template,
            rows = listOf(mapOf("id" to "1", "value" to "2")),
            artifactDir = dir,
            expectedArtifacts = mapOf("artifact.csv" to hash),
        )
        assertFalse(result.accepted)
        assertTrue(result.validationMessages.any { it.contains("hash mismatch") })
    }

    @Test
    fun missingArtifactIsReported() {
        val dir = createTempDirectory("pipeline-missing")
        val pipeline = PrepareOnlyPipeline()
        val result = pipeline.prepare(
            template,
            rows = listOf(mapOf("id" to "1", "value" to "2")),
            artifactDir = dir,
            expectedArtifacts = mapOf("gone.csv" to "hash"),
        )
        assertFalse(result.accepted)
        assertTrue(result.validationMessages.any { it.contains("Missing artifact") })
    }

    @Test
    fun validationRowCheckWorksDirectly() {
        val pipeline = PrepareOnlyPipeline()
        assertEquals(0, pipeline.validateRow(template, mapOf("id" to "1", "value" to "2")).size)
        assertEquals(1, pipeline.validateRow(template, mapOf("id" to "1")).size)
    }
}
