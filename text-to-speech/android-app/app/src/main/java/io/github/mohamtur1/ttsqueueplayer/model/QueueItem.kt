package io.github.mohamtur1.ttsqueueplayer.model

/** Where a queue item came from. */
enum class ItemSource { FILE, PASTE, SHARE }

/** One entry in the reading queue: a loaded file, a pasted text, or a shared text. */
data class QueueItem(
    val id: String,
    var title: String,
    val source: ItemSource,
    val text: String
) {
    val wordCount: Int get() = text.split(Regex("""\s+""")).count { it.isNotEmpty() }
}
