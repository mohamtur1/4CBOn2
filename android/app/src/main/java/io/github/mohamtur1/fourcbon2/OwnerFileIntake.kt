package io.github.mohamtur1.fourcbon2

private val FILE_MIME_TYPE = Regex("[a-z0-9][a-z0-9.+-]{0,63}/[a-z0-9][a-z0-9.+-]{0,63}")
private val FILE_DRAFT_ID = Regex("[A-Za-z0-9._:-]{1,64}")

/** Hard caps for owner-picked file metadata. A policy cannot be created outside these. */
object FileIntakeBounds {
    /** Largest file this prototype will accept, in bytes. */
    const val MAX_BYTES = 5L * 1024L * 1024L

    const val MAX_LABEL_LENGTH = 120
    const val MAX_HANDLE_LENGTH = 2_048
}

/** Why the owner wants this file, declared up front and carried with the decision. */
enum class FilePurpose {
    REFERENCE,
    DRAFT_ATTACHMENT,
    HANDOFF_PACKAGE,
}

/**
 * Raw values as reported by a platform picker. These are untrusted: they are validated
 * into [OwnerFileMetadata] before any decision is made.
 *
 * This type has no content, so an untrusted descriptor can never smuggle bytes into the
 * decision logic.
 */
data class OwnerFileDescriptor(
    val displayLabel: String,
    val mimeType: String,
    val sizeBytes: Long,
    val pickedAtEpochMillis: Long,
)

/**
 * Bounded, validated provenance metadata for one owner-picked file.
 *
 * It carries **no content** and never will in this division: there is no field, method,
 * or constructor that accepts bytes, a stream, or a path.
 */
data class OwnerFileMetadata(
    val displayLabel: String,
    val mimeType: String,
    val sizeBytes: Long,
    val pickedAtEpochMillis: Long,
) {
    init {
        require(displayLabel.isNotBlank()) { "displayLabel must not be blank." }
        require(displayLabel.length <= FileIntakeBounds.MAX_LABEL_LENGTH) {
            "displayLabel must be at most ${FileIntakeBounds.MAX_LABEL_LENGTH} characters."
        }
        require(displayLabel.none { it == '\n' || it == '\r' || it == '\t' }) {
            "displayLabel must not contain control separators."
        }
        require(FILE_MIME_TYPE.matches(mimeType)) {
            "mimeType must look like type/subtype in lowercase, for example text/plain."
        }
        require(sizeBytes >= 0L) { "sizeBytes must not be negative." }
        require(pickedAtEpochMillis > 0L) { "pickedAtEpochMillis must be positive." }
    }

    companion object {
        /** Validates untrusted platform values, or throws `IllegalArgumentException`. */
        fun from(descriptor: OwnerFileDescriptor): OwnerFileMetadata = OwnerFileMetadata(
            displayLabel = descriptor.displayLabel,
            mimeType = descriptor.mimeType,
            sizeBytes = descriptor.sizeBytes,
            pickedAtEpochMillis = descriptor.pickedAtEpochMillis,
        )
    }
}

/**
 * The owner's allow-list of acceptable file types, plus a size cap.
 *
 * Matching is exact: there are no wildcards, no prefix matching, and no fallback, so a
 * type the owner did not name is refused rather than guessed at.
 */
class FileIntakePolicy(
    val allowedMimeTypes: Set<String>,
    val maxBytes: Long = FileIntakeBounds.MAX_BYTES,
) {
    init {
        require(allowedMimeTypes.isNotEmpty()) { "At least one allowed MIME type is required." }
        allowedMimeTypes.forEach { mimeType ->
            require(FILE_MIME_TYPE.matches(mimeType)) {
                "Allowed MIME types must look like type/subtype in lowercase, for example text/plain."
            }
        }
        require(maxBytes in 1L..FileIntakeBounds.MAX_BYTES) {
            "maxBytes must be between 1 and ${FileIntakeBounds.MAX_BYTES}."
        }
    }

    fun allows(mimeType: String): Boolean = allowedMimeTypes.contains(mimeType)

    companion object {
        /** A deliberately narrow default set for text and common document handoffs. */
        val DEFAULT = FileIntakePolicy(
            allowedMimeTypes = setOf(
                "text/plain",
                "text/markdown",
                "application/pdf",
                "image/png",
                "image/jpeg",
            ),
        )
    }
}

/**
 * Narrow boundary for an owner-initiated pick.
 *
 * Implementations may answer **only** "what did the owner choose?" for the exact handle
 * they were given. They must never walk directories, scan a media store, enumerate the
 * filesystem, request broad storage access, or return file content.
 */
fun interface OwnerFileSource {
    fun describe(handle: String): OwnerFileDescriptor?

    companion object {
        /** Conservative default: nothing has been picked. */
        val NONE: OwnerFileSource = OwnerFileSource { null }
    }
}

/** Why a picked file was refused. Every refusal names exactly one cause. */
enum class FileRefusal {
    UNSELECTED,
    MISSING,
    INVALID_METADATA,
    EMPTY_FILE,
    TOO_LARGE,
    TYPE_NOT_ALLOWED,
}

/**
 * The result of evaluating one owner pick.
 *
 * A refusal deliberately does **not** echo the handle: a platform handle can reveal a
 * filename or path fragment, and a refusal record is exactly the kind of thing that gets
 * logged.
 */
sealed interface FileIntakeDecision {
    val purpose: FilePurpose

    data class Accepted(
        override val purpose: FilePurpose,
        val metadata: OwnerFileMetadata,
    ) : FileIntakeDecision

    data class Refused(
        override val purpose: FilePurpose,
        val reason: FileRefusal,
    ) : FileIntakeDecision
}

/**
 * Decides whether one owner-picked file is acceptable, using metadata only.
 *
 * The size cap is applied to the descriptor's reported size **before** a type check and
 * before anything could be read. In this division there is no read step at all, so
 * "before content" is enforced structurally rather than by ordering.
 *
 * Checks run in a fixed, fail-closed order: unselected → missing → invalid metadata →
 * empty → too large → wrong type. A source that throws is treated as missing, never as
 * permission.
 */
class OwnerFileIntake(
    private val policy: FileIntakePolicy = FileIntakePolicy.DEFAULT,
    private val source: OwnerFileSource = OwnerFileSource.NONE,
) {
    fun evaluate(handle: String, purpose: FilePurpose = FilePurpose.REFERENCE): FileIntakeDecision {
        if (handle.isBlank() || handle.length > FileIntakeBounds.MAX_HANDLE_LENGTH) {
            return FileIntakeDecision.Refused(purpose = purpose, reason = FileRefusal.UNSELECTED)
        }

        val descriptor = try {
            source.describe(handle)
        } catch (error: Exception) {
            null
        } ?: return FileIntakeDecision.Refused(purpose = purpose, reason = FileRefusal.MISSING)

        val metadata = try {
            OwnerFileMetadata.from(descriptor)
        } catch (error: IllegalArgumentException) {
            return FileIntakeDecision.Refused(purpose = purpose, reason = FileRefusal.INVALID_METADATA)
        }

        if (metadata.sizeBytes == 0L) {
            return FileIntakeDecision.Refused(purpose = purpose, reason = FileRefusal.EMPTY_FILE)
        }
        if (metadata.sizeBytes > policy.maxBytes) {
            return FileIntakeDecision.Refused(purpose = purpose, reason = FileRefusal.TOO_LARGE)
        }
        if (!policy.allows(metadata.mimeType)) {
            return FileIntakeDecision.Refused(purpose = purpose, reason = FileRefusal.TYPE_NOT_ALLOWED)
        }

        return FileIntakeDecision.Accepted(purpose = purpose, metadata = metadata)
    }
}

/**
 * A request to export one local draft's text. The owner must confirm it explicitly by
 * supplying `ownerConfirmedAtEpochMillis`; a null value means "not confirmed yet".
 */
data class OwnerExportRequest(
    val draftId: String,
    val displayLabel: String,
    val purpose: FilePurpose,
    val ownerConfirmedAtEpochMillis: Long? = null,
) {
    init {
        require(FILE_DRAFT_ID.matches(draftId)) {
            "draftId must be 1-64 characters using letters, digits, dot, underscore, colon, or hyphen."
        }
        require(displayLabel.isNotBlank()) { "displayLabel must not be blank." }
        require(displayLabel.length <= FileIntakeBounds.MAX_LABEL_LENGTH) {
            "displayLabel must be at most ${FileIntakeBounds.MAX_LABEL_LENGTH} characters."
        }
        require(displayLabel.none { it == '\n' || it == '\r' || it == '\t' }) {
            "displayLabel must not contain control separators."
        }
    }

    val isOwnerConfirmed: Boolean get() = ownerConfirmedAtEpochMillis != null
}

enum class ExportRefusal {
    NOT_OWNER_CONFIRMED,
    DRAFT_UNKNOWN,
}

/**
 * What an approved export would write. It is a **description**, not the data: producing
 * the actual bytes is deliberately left to a later, separately scoped division.
 */
data class ExportPlan(
    val draftId: String,
    val displayLabel: String,
    val purpose: FilePurpose,
    val confirmedAtEpochMillis: Long,
)

sealed interface ExportDecision {
    data class Planned(val plan: ExportPlan) : ExportDecision

    data class Refused(val reason: ExportRefusal) : ExportDecision
}

/**
 * Turns an owner export request into a plan, or refuses it.
 *
 * Nothing here reads a draft, opens a destination, writes a file, or contacts a service.
 */
class FileExportPlanner(
    private val draftExists: (String) -> Boolean,
) {
    fun plan(request: OwnerExportRequest): ExportDecision {
        val confirmedAt = request.ownerConfirmedAtEpochMillis
            ?: return ExportDecision.Refused(reason = ExportRefusal.NOT_OWNER_CONFIRMED)
        if (!draftExists(request.draftId)) {
            return ExportDecision.Refused(reason = ExportRefusal.DRAFT_UNKNOWN)
        }
        return ExportDecision.Planned(
            plan = ExportPlan(
                draftId = request.draftId,
                displayLabel = request.displayLabel,
                purpose = request.purpose,
                confirmedAtEpochMillis = confirmedAt,
            ),
        )
    }
}
