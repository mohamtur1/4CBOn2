package io.github.mohamtur1.ttsqueueplayer.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One speakable piece of a document.
 *
 * [startOffset]/[endOffset] point into the ORIGINAL document text so the UI can
 * highlight exactly the words being read (the yellow highlight in the original HTML).
 * [text] is `original.substring(startOffset, endOffset)` with surrounding whitespace trimmed.
 */
data class Chunk(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val pauseAfterMs: Int
) {
    val wordCount: Int get() = text.split(Regex("""\s+""")).count { it.isNotEmpty() }
}

/**
 * Port of the chunking + natural-pause logic from the original TextToSpeech.html
 * (`chunkText()` + `getPauseDuration()`), adapted to also track character offsets
 * for highlighting, and to cap chunk length so Pause/Rewind feel responsive
 * (Android TTS can only stop/restart at utterance boundaries, so small pieces =
 * pause/rewind land within about a sentence).
 *
 * Rules (same as the HTML):
 *  - split into sentences at . ! ? and line breaks,
 *  - sentences longer than ~60 chars are further split at commas / semicolons /
 *    colons / "and" / "or" / "but", avoiding tiny fragments (< ~30 chars),
 *  - pause after a piece depends on how it ends (sentence end > clause > default),
 *    with a longer pause at paragraph breaks; pauses shrink as speed increases.
 */
object TextChunker {

    private const val LONG_SENTENCE = 60
    private const val MIN_CLAUSE = 30
    private const val MAX_CHUNK_CHARS = 320

    fun formatText(raw: String): String =
        raw.replace("\r\n", "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .replace(Regex("""[ \t]+"""), " ")
            .trim()

    fun chunk(original: String): List<Chunk> {
        if (original.isBlank()) return emptyList()

        val ranges = mutableListOf<IntRange>() // inclusive-exclusive [first, last+1) via IntRange semantics below
        for ((sStart, sEnd) in sentenceRanges(original)) {
            if (sEnd - sStart <= LONG_SENTENCE) {
                addCapped(ranges, original, sStart, sEnd)
            } else {
                for ((cStart, cEnd) in clauseRanges(original, sStart, sEnd)) {
                    addCapped(ranges, original, cStart, cEnd)
                }
            }
        }

        // Build chunks (trimmed), computing the pause from the ending punctuation
        // and from the gap to the next chunk (paragraph break = longer pause).
        val trimmed = ranges.mapNotNull { r ->
            var a = r.first
            var b = r.last + 1
            while (a < b && original[a].isWhitespace()) a++
            while (b > a && original[b - 1].isWhitespace()) b--
            if (a >= b) null else a to b
        }

        return trimmed.mapIndexed { i, (a, b) ->
            val gap = if (i + 1 < trimmed.size) original.substring(b, trimmed[i + 1].first) else ""
            Chunk(
                text = original.substring(a, b),
                startOffset = a,
                endOffset = b,
                pauseAfterMs = pauseAfter(original.substring(a, b), gap)
            )
        }
    }

    /** Base pause (ms) after a chunk, before speed adjustment. Mirrors the HTML values. */
    fun pauseAfter(chunkText: String, gapToNext: String = ""): Int {
        if (gapToNext.contains("\n\n") || gapToNext.contains("\n \n")) return 450
        val t = chunkText.trimEnd()
        return when {
            t.isEmpty() -> 100
            t.last() == '.' || t.last() == '!' || t.last() == '?' -> 350
            t.last() == ',' || t.last() == ';' || t.last() == ':' -> 180
            gapToNext.contains('\n') -> 220
            else -> 100
        }
    }

    /** HTML behaviour: pauses shrink with the square root of the speech rate. */
    fun speedAdjustedPauseMs(chunk: Chunk, rate: Float): Long =
        (chunk.pauseAfterMs / sqrt(max(rate, 0.1f).toDouble())).toLong().coerceIn(0L, 2000L)

    // ---------------------------------------------------------------------

    /** Sentence boundaries: [.!?] followed by whitespace/end, or a line break. */
    private fun sentenceRanges(text: String): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val isSentenceEnd = (c == '.' || c == '!' || c == '?') &&
                (i + 1 >= text.length || text[i + 1].isWhitespace())
            val isLineBreak = c == '\n'
            if (isSentenceEnd || isLineBreak) {
                val end = if (isLineBreak) i else i + 1
                if (end > start) out.add(start to end)
                start = i + 1
            }
            i++
        }
        if (start < text.length) out.add(start to text.length)
        return out
    }

    /** Split a long sentence at clause delimiters, merging fragments shorter than MIN_CLAUSE. */
    private fun clauseRanges(text: String, from: Int, to: Int): List<Pair<Int, Int>> {
        val delimiters = listOf(", ", "; ", ": ", " and ", " or ", " but ", " And ", " Or ", " But ")
        val splitPoints = mutableListOf<Int>() // index just AFTER the delimiter
        var i = from
        while (i < to) {
            val matched = delimiters.firstOrNull { d ->
                i + d.length <= to && text.regionMatches(i, d, 0, d.length)
            }
            if (matched != null) {
                splitPoints.add(i + matched.length)
                i += matched.length
            } else {
                i++
            }
        }
        if (splitPoints.isEmpty()) return listOf(from to)

        val out = mutableListOf<Pair<Int, Int>>()
        var pieceStart = from
        for (point in splitPoints) {
            if (point - pieceStart >= MIN_CLAUSE) {
                out.add(pieceStart to point)
                pieceStart = point
            }
        }
        if (pieceStart < to) {
            // Avoid a tiny tail: merge it into the previous piece when very short.
            if (out.isNotEmpty() && to - pieceStart < MIN_CLAUSE) {
                val last = out.removeAt(out.size - 1)
                out.add(last.first to to)
            } else {
                out.add(pieceStart to to)
            }
        }
        return out
    }

    /** Hard cap: split anything still longer than MAX_CHUNK_CHARS at word boundaries. */
    private fun addCapped(ranges: MutableList<IntRange>, text: String, from: Int, to: Int) {
        var a = from
        while (to - a > MAX_CHUNK_CHARS) {
            var cut = min(a + MAX_CHUNK_CHARS, to)
            while (cut > a && !text[cut - 1].isWhitespace()) cut--
            if (cut == a) cut = min(a + MAX_CHUNK_CHARS, to)
            ranges.add(a until cut)
            a = cut
        }
        if (a < to) ranges.add(a until to)
    }
}
