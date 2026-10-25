package io.github.mohamtur1.fourcbon2

import java.net.URI
import java.net.URISyntaxException

/** A validated competition page for an explicit owner-initiated browser handoff. */
class KaggleCompetitionReference private constructor(
    val url: String,
) {
    companion object {
        private val competitionPath = Regex(
            pattern = "^/competitions/([A-Za-z0-9][A-Za-z0-9-]{0,99})/?$",
        )

        /** Returns null for empty, malformed, non-Kaggle, non-HTTPS, or non-competition input. */
        fun parse(input: String): KaggleCompetitionReference? {
            val candidate = input.trim()
            if (candidate.isEmpty() || candidate.length > 512) return null

            val uri = try {
                URI(candidate)
            } catch (_: URISyntaxException) {
                return null
            }
            if (uri.isOpaque) return null
            if (uri.scheme?.equals("https", ignoreCase = true) != true) return null
            if (uri.host?.equals("www.kaggle.com", ignoreCase = true) != true) return null
            if (uri.port != -1 || uri.userInfo != null) return null
            if (uri.rawQuery != null || uri.rawFragment != null) return null

            val match = competitionPath.matchEntire(uri.rawPath ?: return null) ?: return null
            val competitionSlug = match.groupValues[1]
            return KaggleCompetitionReference(
                url = "https://www.kaggle.com/competitions/$competitionSlug",
            )
        }
    }
}

/** Secret-free UI state. It deliberately has no joined, submitted, or synchronized status. */
data class KaggleManualHandoffState(
    val referencePresent: Boolean = false,
    val notice: Notice = Notice.NONE,
) {
    val canOpen: Boolean
        get() = referencePresent

    fun onReferenceChanged(validReferencePresent: Boolean): KaggleManualHandoffState = copy(
        referencePresent = validReferencePresent,
        notice = Notice.NONE,
    )

    fun onOpenRequested(): KaggleManualHandoffState = if (referencePresent) {
        copy(notice = Notice.OPEN_REQUESTED)
    } else {
        copy(notice = Notice.INVALID_REFERENCE)
    }

    fun onOpenFailed(): KaggleManualHandoffState = copy(notice = Notice.OPEN_FAILED)

    fun onCancel(): KaggleManualHandoffState = copy(
        referencePresent = false,
        notice = Notice.CANCELLED,
    )

    enum class Notice {
        NONE,
        INVALID_REFERENCE,
        OPEN_REQUESTED,
        OPEN_FAILED,
        CANCELLED,
    }
}
