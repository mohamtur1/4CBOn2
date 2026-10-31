package io.github.mohamtur1.fourcbon2.timeline

import io.github.mohamtur1.fourcbon2.local.LocalDocumentStore
import io.github.mohamtur1.fourcbon2.local.TimelineEventKind
import io.github.mohamtur1.fourcbon2.local.TimelineEventStatus
import kotlin.io.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionTimelineTest {

    @Test
    fun recordsPlanActionResultCheckEvents() {
        val timeline = ActionTimeline(LocalDocumentStore(createTempDirectory("timeline")))
        timeline.load()
        timeline.record(TimelineEventKind.PLAN, "Find evaluation metric", TimelineEventStatus.ATTEMPTED)
        timeline.record(TimelineEventKind.ACTION, "Opened evaluation page", TimelineEventStatus.MANUAL)
        timeline.record(TimelineEventKind.RESULT, "Saved passage and URL", TimelineEventStatus.VERIFIED)
        timeline.record(TimelineEventKind.CHECK, "Passage supports metric", TimelineEventStatus.VERIFIED)

        assertEquals(4, timeline.all().size)
        assertEquals(1, timeline.eventsOfKind(TimelineEventKind.PLAN).size)
    }

    @Test
    fun uncertainResultsAreNotVerified() {
        val timeline = ActionTimeline(LocalDocumentStore(createTempDirectory("timeline-uncertain")))
        timeline.load()
        val event = timeline.record(
            TimelineEventKind.RESULT,
            "Submission may have been accepted",
            TimelineEventStatus.UNCERTAIN,
        )
        assertFalse(timeline.isVerified(event.id))
        val verified = timeline.record(TimelineEventKind.CHECK, "Confirmed by owner", TimelineEventStatus.VERIFIED)
        assertTrue(timeline.isVerified(verified.id))
    }

    @Test
    fun timelineSurvivesRestart() {
        val dir = createTempDirectory("timeline-restart")
        val first = ActionTimeline(LocalDocumentStore(dir))
        first.load()
        first.record(TimelineEventKind.ACTION, "Opened page", TimelineEventStatus.VERIFIED)

        val second = ActionTimeline(LocalDocumentStore(dir))
        second.load()
        assertEquals(1, second.all().size)
        assertEquals("Opened page", second.all()[0].description)
    }
}
