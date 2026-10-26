package io.github.mohamtur1.ttsqueueplayer.core

/** The user's hard requirements about the queue, kept pure so they can be unit-tested. */
object QueueRules {

    const val MAX_ITEMS = 10

    fun canAdd(currentCount: Int, adding: Int = 1): Boolean =
        adding > 0 && currentCount + adding <= MAX_ITEMS

    fun slotsLeft(currentCount: Int): Int = (MAX_ITEMS - currentCount).coerceAtLeast(0)

    /**
     * Title for a pasted/shared text (which has no filename): its first non-blank
     * line, shortened; or a numbered fallback like "Pasted text 2".
     */
    fun autoTitle(text: String, fallbackNumber: Int): String {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (firstLine.isEmpty()) return "Pasted text $fallbackNumber"
        return if (firstLine.length > 60) firstLine.take(57).trimEnd() + "…" else firstLine
    }

    /** Title for a loaded file: display name without the .txt extension. */
    fun fileTitle(displayName: String): String {
        val name = displayName.trim().ifEmpty { "Document" }
        return name.removeSuffix(".txt").removeSuffix(".TXT")
    }
}
