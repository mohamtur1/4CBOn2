package io.github.mohamtur1.fourcbon2

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerFileIntakeTest {
    @Test
    fun validatedMetadataAcceptsBoundedValues() {
        val metadata = OwnerFileMetadata(
            displayLabel = "Synthetic report.txt",
            mimeType = "text/plain",
            sizeBytes = 1_024L,
            pickedAtEpochMillis = 1_700_000_000_000L,
        )

        assertEquals("Synthetic report.txt", metadata.displayLabel)
        assertEquals("text/plain", metadata.mimeType)
        assertEquals(1_024L, metadata.sizeBytes)
        assertEquals(1_700_000_000_000L, metadata.pickedAtEpochMillis)

        // The same values round-trip through the untrusted-descriptor validator.
        val descriptor = OwnerFileDescriptor(
            displayLabel = metadata.displayLabel,
            mimeType = metadata.mimeType,
            sizeBytes = metadata.sizeBytes,
            pickedAtEpochMillis = metadata.pickedAtEpochMillis,
        )
        assertEquals(metadata, OwnerFileMetadata.from(descriptor))
    }

    @Test
    fun metadataRejectsBlankOrControlLabelsOverlongTextAndMalformedMimeTypes() {
        assertThrows(IllegalArgumentException::class.java) { syntheticMetadata(displayLabel = "   ") }
        assertThrows(IllegalArgumentException::class.java) { syntheticMetadata(displayLabel = "line\nbreak") }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticMetadata(displayLabel = "a".repeat(FileIntakeBounds.MAX_LABEL_LENGTH + 1))
        }
        listOf("", "plaintext", "Text/Plain", "text/", "/plain", "text/pla in", "*/*").forEach { mime ->
            assertThrows("mime should be rejected: $mime", IllegalArgumentException::class.java) {
                syntheticMetadata(mimeType = mime)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { syntheticMetadata(sizeBytes = -1L) }
        assertThrows(IllegalArgumentException::class.java) { syntheticMetadata(pickedAtEpochMillis = 0L) }
    }

    @Test
    fun policyRejectsEmptyAllowListsNonExactTypesAndOversizedCaps() {
        assertThrows(IllegalArgumentException::class.java) { FileIntakePolicy(emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { FileIntakePolicy(setOf("*/*")) }
        assertThrows(IllegalArgumentException::class.java) { FileIntakePolicy(setOf("text/*")) }
        assertThrows(IllegalArgumentException::class.java) { FileIntakePolicy(setOf("text/plain"), maxBytes = 0L) }
        assertThrows(IllegalArgumentException::class.java) {
            FileIntakePolicy(setOf("text/plain"), maxBytes = FileIntakeBounds.MAX_BYTES + 1L)
        }

        val policy = FileIntakePolicy(setOf("text/plain"))
        assertTrue(policy.allows("text/plain"))
        assertFalse(policy.allows("text/markdown"))
        assertFalse(policy.allows("TEXT/PLAIN"))
        assertFalse(policy.allows("text/plain;charset=utf-8"))
    }

    @Test
    fun blankOrOverlongHandleIsRefusedAsUnselectedWithoutConsultingTheSource() {
        val source = RecordingSource(descriptor = syntheticDescriptor())
        val intake = OwnerFileIntake(FileIntakePolicy.DEFAULT, source)

        assertEquals(
            FileIntakeDecision.Refused(FilePurpose.REFERENCE, FileRefusal.UNSELECTED),
            intake.evaluate(""),
        )
        assertEquals(
            FileRefusal.UNSELECTED,
            (intake.evaluate("   ") as FileIntakeDecision.Refused).reason,
        )
        assertEquals(
            FileRefusal.UNSELECTED,
            (intake.evaluate("h".repeat(FileIntakeBounds.MAX_HANDLE_LENGTH + 1)) as FileIntakeDecision.Refused).reason,
        )
        assertTrue(source.calls.isEmpty())
    }

    @Test
    fun missingDescriptorIsRefusedAsMissing() {
        val intake = OwnerFileIntake(FileIntakePolicy.DEFAULT, OwnerFileSource.NONE)

        assertEquals(
            FileIntakeDecision.Refused(FilePurpose.DRAFT_ATTACHMENT, FileRefusal.MISSING),
            intake.evaluate("content://synthetic/missing", FilePurpose.DRAFT_ATTACHMENT),
        )
    }

    @Test
    fun throwingSourceIsTreatedAsMissingRatherThanAsPermission() {
        val intake = OwnerFileIntake(
            FileIntakePolicy.DEFAULT,
            OwnerFileSource { throw IllegalStateException("synthetic picker failure") },
        )

        assertEquals(
            FileRefusal.MISSING,
            (intake.evaluate("content://synthetic/throws") as FileIntakeDecision.Refused).reason,
        )
    }

    @Test
    fun invalidMetadataFromAnUntrustedSourceIsRefused() {
        val intake = OwnerFileIntake(
            FileIntakePolicy.DEFAULT,
            RecordingSource(descriptor = syntheticDescriptor(mimeType = "Text/Plain")),
        )
        assertEquals(
            FileRefusal.INVALID_METADATA,
            (intake.evaluate("content://synthetic/bad-mime") as FileIntakeDecision.Refused).reason,
        )

        val badLabel = OwnerFileIntake(
            FileIntakePolicy.DEFAULT,
            RecordingSource(descriptor = syntheticDescriptor(displayLabel = "")),
        )
        assertEquals(
            FileRefusal.INVALID_METADATA,
            (badLabel.evaluate("content://synthetic/bad-label") as FileIntakeDecision.Refused).reason,
        )
    }

    @Test
    fun emptyFileIsRefused() {
        val intake = OwnerFileIntake(
            FileIntakePolicy.DEFAULT,
            RecordingSource(descriptor = syntheticDescriptor(sizeBytes = 0L)),
        )

        assertEquals(
            FileRefusal.EMPTY_FILE,
            (intake.evaluate("content://synthetic/empty") as FileIntakeDecision.Refused).reason,
        )
    }

    @Test
    fun oversizedFileIsRefusedFromReportedSizeAlone() {
        val source = RecordingSource(descriptor = syntheticDescriptor(sizeBytes = FileIntakeBounds.MAX_BYTES + 1L))
        val intake = OwnerFileIntake(FileIntakePolicy.DEFAULT, source)

        assertEquals(
            FileRefusal.TOO_LARGE,
            (intake.evaluate("content://synthetic/huge") as FileIntakeDecision.Refused).reason,
        )
        assertEquals(1, source.calls.size)
    }

    @Test
    fun sizeIsCheckedBeforeTypeSoAnOversizedDisallowedFileReportsSizeFirst() {
        val intake = OwnerFileIntake(
            FileIntakePolicy(setOf("text/plain"), maxBytes = 10L),
            RecordingSource(
                descriptor = syntheticDescriptor(mimeType = "application/zip", sizeBytes = 11L),
            ),
        )

        assertEquals(
            FileRefusal.TOO_LARGE,
            (intake.evaluate("content://synthetic/both-wrong") as FileIntakeDecision.Refused).reason,
        )

        val smaller = OwnerFileIntake(
            FileIntakePolicy(setOf("text/plain"), maxBytes = 10L),
            RecordingSource(
                descriptor = syntheticDescriptor(mimeType = "application/zip", sizeBytes = 10L),
            ),
        )
        assertEquals(
            FileRefusal.TYPE_NOT_ALLOWED,
            (smaller.evaluate("content://synthetic/wrong-type") as FileIntakeDecision.Refused).reason,
        )
    }

    @Test
    fun allowedFileIsAcceptedWithItsMetadataAndDeclaredPurpose() {
        val descriptor = syntheticDescriptor()
        val intake = OwnerFileIntake(FileIntakePolicy.DEFAULT, RecordingSource(descriptor = descriptor))

        val decision = intake.evaluate("content://synthetic/report", FilePurpose.HANDOFF_PACKAGE)

        assertEquals(
            FileIntakeDecision.Accepted(
                purpose = FilePurpose.HANDOFF_PACKAGE,
                metadata = OwnerFileMetadata.from(descriptor),
            ),
            decision,
        )
    }

    @Test
    fun sizeCapBoundaryIsInclusiveAndOnlyDescribeIsConsulted() {
        val source = RecordingSource(descriptor = syntheticDescriptor(sizeBytes = FileIntakeBounds.MAX_BYTES))
        val intake = OwnerFileIntake(FileIntakePolicy.DEFAULT, source)

        assertTrue(intake.evaluate("content://synthetic/exact-cap") is FileIntakeDecision.Accepted)
        assertEquals(
            "the owner pick must be described exactly once",
            listOf("content://synthetic/exact-cap"),
            source.calls,
        )
    }

    @Test
    fun fileValueLayerExposesNoContentReadingOrWritingApi() {
        val forbidden = listOf(
            ByteArray::class.java,
            InputStream::class.java,
            OutputStream::class.java,
            Reader::class.java,
            Writer::class.java,
            File::class.java,
            java.nio.file.Path::class.java,
        )
        val classes = listOf(
            OwnerFileSource::class.java,
            OwnerFileIntake::class.java,
            OwnerFileMetadata::class.java,
            OwnerFileDescriptor::class.java,
            FileIntakePolicy::class.java,
            OwnerExportRequest::class.java,
            FileExportPlanner::class.java,
            ExportPlan::class.java,
        )

        val offending = classes.flatMap { type ->
            type.declaredMethods.flatMap { method ->
                val types = method.parameterTypes.toList() + listOf(method.returnType)
                types.filter { candidate -> forbidden.any { blocked -> blocked == candidate } }
                    .map { blocked -> "${type.simpleName}.${method.name} uses ${blocked.simpleName}" }
            }
        }

        assertEquals("no content or filesystem accessor may exist", emptyList<String>(), offending)
    }

    @Test
    fun exportRequestValidatesIdentifierLabelAndDefaultsToUnconfirmed() {
        assertThrows(IllegalArgumentException::class.java) {
            syntheticExportRequest(draftId = "bad draft id")
        }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticExportRequest(displayLabel = "  ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticExportRequest(displayLabel = "tab\there")
        }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticExportRequest(displayLabel = "a".repeat(FileIntakeBounds.MAX_LABEL_LENGTH + 1))
        }

        assertFalse(syntheticExportRequest().isOwnerConfirmed)
        assertTrue(syntheticExportRequest(ownerConfirmedAtEpochMillis = 1L).isOwnerConfirmed)
    }

    @Test
    fun unconfirmedExportIsRefusedWithoutCheckingTheDraft() {
        var lookups = 0
        val planner = FileExportPlanner { lookups++; true }

        assertEquals(
            ExportDecision.Refused(ExportRefusal.NOT_OWNER_CONFIRMED),
            planner.plan(syntheticExportRequest(ownerConfirmedAtEpochMillis = null)),
        )
        assertEquals("an unconfirmed export must not be looked up", 0, lookups)
    }

    @Test
    fun confirmedExportForAnUnknownDraftIsRefused() {
        val planner = FileExportPlanner { exists -> exists == "draft-known" }

        assertEquals(
            ExportDecision.Refused(ExportRefusal.DRAFT_UNKNOWN),
            planner.plan(syntheticExportRequest(draftId = "draft-other", ownerConfirmedAtEpochMillis = 1L)),
        )
    }

    @Test
    fun confirmedExportProducesAPlanRatherThanProducingContent() {
        val planner = FileExportPlanner { exists -> exists == "draft-known" }

        val decision = planner.plan(
            syntheticExportRequest(draftId = "draft-known", ownerConfirmedAtEpochMillis = 1_700_000_000_000L),
        )

        assertEquals(
            ExportDecision.Planned(
                ExportPlan(
                    draftId = "draft-known",
                    displayLabel = "Synthetic handoff.txt",
                    purpose = FilePurpose.HANDOFF_PACKAGE,
                    confirmedAtEpochMillis = 1_700_000_000_000L,
                ),
            ),
            decision,
        )
    }

    private fun syntheticDescriptor(
        displayLabel: String = "Synthetic report.txt",
        mimeType: String = "text/plain",
        sizeBytes: Long = 2_048L,
    ) = OwnerFileDescriptor(
        displayLabel = displayLabel,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        pickedAtEpochMillis = 1_700_000_000_000L,
    )

    private fun syntheticMetadata(
        displayLabel: String = "Synthetic report.txt",
        mimeType: String = "text/plain",
        sizeBytes: Long = 2_048L,
        pickedAtEpochMillis: Long = 1_700_000_000_000L,
    ) = OwnerFileMetadata(
        displayLabel = displayLabel,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        pickedAtEpochMillis = pickedAtEpochMillis,
    )

    private fun syntheticExportRequest(
        draftId: String = "draft-known",
        displayLabel: String = "Synthetic handoff.txt",
        purpose: FilePurpose = FilePurpose.HANDOFF_PACKAGE,
        ownerConfirmedAtEpochMillis: Long? = null,
    ) = OwnerExportRequest(
        draftId = draftId,
        displayLabel = displayLabel,
        purpose = purpose,
        ownerConfirmedAtEpochMillis = ownerConfirmedAtEpochMillis,
    )

    private class RecordingSource(private val descriptor: OwnerFileDescriptor?) : OwnerFileSource {
        val calls = mutableListOf<String>()

        override fun describe(handle: String): OwnerFileDescriptor? {
            calls += handle
            return descriptor
        }
    }
}
