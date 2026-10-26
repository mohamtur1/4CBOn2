package io.github.mohamtur1.ttsqueueplayer.core

import kotlin.math.max

/**
 * Time estimation for synthetic speech.
 *
 * TTS output is not an audio file, so there is no real timeline to seek in.
 * "Rewind 5 seconds" is therefore implemented as: walk backwards over chunks,
 * adding up each chunk's ESTIMATED spoken duration at the current speed, until
 * roughly 5 seconds' worth of speech has been covered; then resume speaking
 * from there. At rate 1.0 a typical Android voice reads about 150-160 words
 * per minute, i.e. ~2.6 words/second.
 */
object SpeechEstimate {

    const val WORDS_PER_SECOND_AT_RATE_1 = 2.6
    const val SEEK_SECONDS = 5.0

    fun secondsFor(chunk: Chunk, rate: Float): Double {
        val r = max(rate.toDouble(), 0.1)
        return max(chunk.wordCount / (WORDS_PER_SECOND_AT_RATE_1 * r), 0.3)
    }

    fun totalSeconds(chunks: List<Chunk>, rate: Float): Double =
        chunks.sumOf { secondsFor(it, rate) }

    fun elapsedSecondsBefore(chunks: List<Chunk>, index: Int, rate: Float): Double =
        chunks.take(index.coerceIn(0, chunks.size)).sumOf { secondsFor(it, rate) }

    /**
     * Index to jump to so that about [seconds] of speech will be re-heard.
     * Always moves back at least one chunk (unless already at the start).
     */
    fun rewindTargetIndex(chunks: List<Chunk>, current: Int, rate: Float, seconds: Double = SEEK_SECONDS): Int {
        if (chunks.isEmpty() || current <= 0) return 0
        var acc = 0.0
        var i = current
        while (i > 0 && acc < seconds) {
            i--
            acc += secondsFor(chunks[i], rate)
        }
        return i
    }

    /** Index to jump to in order to skip about [seconds] of speech. */
    fun forwardTargetIndex(chunks: List<Chunk>, current: Int, rate: Float, seconds: Double = SEEK_SECONDS): Int {
        if (chunks.isEmpty()) return 0
        var acc = 0.0
        var i = current
        while (i < chunks.size - 1 && acc < seconds) {
            acc += secondsFor(chunks[i], rate)
            i++
        }
        return i
    }
}
