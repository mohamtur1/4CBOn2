package io.github.mohamtur1.ttsqueueplayer

import io.github.mohamtur1.ttsqueueplayer.core.QueueRules
import io.github.mohamtur1.ttsqueueplayer.core.SpeechEstimate
import io.github.mohamtur1.ttsqueueplayer.core.TextChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the ported HTML logic: chunking, pauses, the ±5 second
 * jump maths, and the 10-item queue rule. Run by CI before the APK is built.
 */
class CoreLogicTest {

    private val sample = """
        What is the uTest Academy?
        The uTest Academy is a training program designed to teach the basic essentials of how to be a tester in the uTest community.

        How does it work? It is comprised of numerous training courses, and each course includes text and video instructions inside of it, so testers can learn at their own pace wherever they are.
    """.trimIndent()

    @Test
    fun chunking_keepsEveryWordExactlyOnce() {
        val chunks = TextChunker.chunk(sample)
        val originalWords = sample.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val chunkWords = chunks.flatMap { it.text.split(Regex("""\s+""")) }.filter { it.isNotEmpty() }
        assertEquals(originalWords, chunkWords)
    }

    @Test
    fun chunking_offsetsPointAtTheChunkText() {
        val chunks = TextChunker.chunk(sample)
        assertTrue(chunks.isNotEmpty())
        for (chunk in chunks) {
            assertEquals(chunk.text, sample.substring(chunk.startOffset, chunk.endOffset))
            assertFalse(chunk.text.first().isWhitespace())
            assertFalse(chunk.text.last().isWhitespace())
        }
        // Chunks are in document order and do not overlap.
        for (i in 1 until chunks.size) {
            assertTrue(chunks[i].startOffset >= chunks[i - 1].endOffset)
        }
    }

    @Test
    fun chunking_longSentencesAreSplitAndCapped() {
        val longSentence = (1..120).joinToString(" ") { "word$it," } + " final."
        val chunks = TextChunker.chunk(longSentence)
        assertTrue("long sentence should be split", chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 320 })
    }

    @Test
    fun chunking_emptyTextGivesNoChunks() {
        assertTrue(TextChunker.chunk("   \n\n  ").isEmpty())
    }

    @Test
    fun pauses_followTheHtmlValues() {
        assertEquals(350, TextChunker.pauseAfter("This is a sentence."))
        assertEquals(180, TextChunker.pauseAfter("A clause,"))
        assertEquals(450, TextChunker.pauseAfter("End of paragraph.", gapToNext = "\n\n"))
        assertTrue(TextChunker.pauseAfter("End of paragraph.", "\n\n") > TextChunker.pauseAfter("A sentence."))
    }

    @Test
    fun pauses_shrinkWhenFaster() {
        val chunk = TextChunker.chunk("Hello world. This is only a test.").first()
        val slow = TextChunker.speedAdjustedPauseMs(chunk, 0.8f)
        val fast = TextChunker.speedAdjustedPauseMs(chunk, 2.5f)
        assertTrue(slow > fast)
    }

    @Test
    fun estimate_durationScalesWithWordsAndRate() {
        val chunks = TextChunker.chunk(sample)
        val atNormal = SpeechEstimate.totalSeconds(chunks, 1.0f)
        val atDouble = SpeechEstimate.totalSeconds(chunks, 2.0f)
        assertTrue(atNormal > 5.0)
        assertEquals(atNormal / 2.0, atDouble, 1.0) // roughly half at double speed
    }

    @Test
    fun rewind_movesBackAboutFiveSecondsOfSpeech() {
        // 40 one-line "sentences" of 5 words each (~1.9s each at rate 1.0).
        val text = (1..40).joinToString("\n") { "Alpha bravo charlie delta echo$it" }
        val chunks = TextChunker.chunk(text)
        val current = 30
        val target = SpeechEstimate.rewindTargetIndex(chunks, current, 1.0f, 5.0)
        assertTrue(target < current)
        val reheardSeconds = (target until current).sumOf { SpeechEstimate.secondsFor(chunks[it], 1.0f) }
        assertTrue("re-heard $reheardSeconds should be near 5s", reheardSeconds in 3.0..9.0)
        // At double speed, 5 seconds covers more chunks.
        val targetFast = SpeechEstimate.rewindTargetIndex(chunks, current, 2.0f, 5.0)
        assertTrue(targetFast <= target)
        // At the very start, rewind stays at 0.
        assertEquals(0, SpeechEstimate.rewindTargetIndex(chunks, 0, 1.0f, 5.0))
    }

    @Test
    fun forward_movesForwardAndStopsAtTheEnd() {
        val text = (1..10).joinToString("\n") { "Alpha bravo charlie delta echo$it" }
        val chunks = TextChunker.chunk(text)
        val target = SpeechEstimate.forwardTargetIndex(chunks, 2, 1.0f, 5.0)
        assertTrue(target > 2)
        assertTrue(target <= chunks.size - 1)
        assertEquals(chunks.size - 1, SpeechEstimate.forwardTargetIndex(chunks, chunks.size - 1, 1.0f, 5.0))
    }

    @Test
    fun queueRules_enforceTheTenItemLimit() {
        assertTrue(QueueRules.canAdd(0))
        assertTrue(QueueRules.canAdd(9))
        assertFalse(QueueRules.canAdd(10))          // 11th item refused
        assertFalse(QueueRules.canAdd(9, adding = 2))
        assertEquals(10, QueueRules.MAX_ITEMS)
        assertEquals(3, QueueRules.slotsLeft(7))    // e.g. 3 files + 4 pastes = 7/10
        assertEquals(0, QueueRules.slotsLeft(10))
    }

    @Test
    fun queueRules_autoTitles() {
        assertEquals(
            "Academy / 1. Introduction to uTest",
            QueueRules.autoTitle("Academy / 1. Introduction to uTest\nuTest Academy\nmore text", 1)
        )
        assertEquals("Pasted text 3", QueueRules.autoTitle("   \n  ", 3))
        val longLine = "x".repeat(100)
        val title = QueueRules.autoTitle(longLine, 1)
        assertTrue(title.length <= 60 && title.endsWith("…"))
        assertEquals("Lesson One", QueueRules.fileTitle("Lesson One.txt"))
        assertEquals("notes", QueueRules.fileTitle("notes"))
    }

    @Test
    fun formatText_matchesTheHtmlFormatting() {
        assertEquals("a b\n\nc", TextChunker.formatText("  a   b\r\n\r\n\r\nc  "))
    }
}
