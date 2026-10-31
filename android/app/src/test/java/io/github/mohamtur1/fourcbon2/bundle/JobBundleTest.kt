package io.github.mohamtur1.fourcbon2.bundle

import kotlin.io.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JobBundleTest {

    @Test
    fun exportAndImportRoundTrip() {
        val dir = createTempDirectory("bundle")
        File(dir, "job.json").writeText("""{"goal":"test"}""")
        val manifest = JobBundle.export(dir, contractVersion = "1.0", allowedDestinations = listOf("job.json"))
        assertEquals(1, manifest.files.size)

        val result = JobBundle.import(
            workspaceDir = dir,
            manifest = manifest,
            expectedContractVersion = "1.0",
            allowedDestinations = listOf("job.json"),
        )
        assertTrue(result.accepted)
    }

    @Test
    fun tamperedFileFailsHashCheck() {
        val dir = createTempDirectory("bundle-tamper")
        File(dir, "job.json").writeText("original")
        val manifest = JobBundle.export(dir, contractVersion = "1.0", allowedDestinations = listOf("job.json"))
        File(dir, "job.json").writeText("tampered")

        val result = JobBundle.import(
            workspaceDir = dir,
            manifest = manifest,
            expectedContractVersion = "1.0",
            allowedDestinations = listOf("job.json"),
        )
        assertFalse(result.accepted)
        assertTrue(result.reason.contains("Hash mismatch"))
    }

    @Test
    fun contractMismatchIsRejected() {
        val dir = createTempDirectory("bundle-contract")
        File(dir, "job.json").writeText("x")
        val manifest = JobBundle.export(dir, contractVersion = "1.0", allowedDestinations = listOf("job.json"))
        val result = JobBundle.import(
            workspaceDir = dir,
            manifest = manifest,
            expectedContractVersion = "2.0",
            allowedDestinations = listOf("job.json"),
        )
        assertFalse(result.accepted)
        assertTrue(result.reason.contains("Contract"))
    }

    @Test
    fun filesOutsideAllowedScopeAreRejected() {
        val dir = createTempDirectory("bundle-scope")
        File(dir, "job.json").writeText("x")
        val manifest = JobBundle.export(dir, contractVersion = "1.0", allowedDestinations = listOf("allowed.txt"))
        val result = JobBundle.import(
            workspaceDir = dir,
            manifest = manifest,
            expectedContractVersion = "1.0",
            allowedDestinations = listOf("allowed.txt"),
        )
        assertFalse(result.accepted)
        assertTrue(result.reason.contains("outside"))
    }

    @Test
    fun unsupportedBundleVersionIsRejected() {
        val dir = createTempDirectory("bundle-version")
        val manifest = JobBundle.Manifest(bundleVersion = 99, contractVersion = "1.0", files = emptyList())
        val result = JobBundle.import(
            workspaceDir = dir,
            manifest = manifest,
            expectedContractVersion = "1.0",
            allowedDestinations = emptyList(),
        )
        assertFalse(result.accepted)
    }
}
