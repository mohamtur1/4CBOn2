package io.github.mohamtur1.fourcbon2.timeline

import io.github.mohamtur1.fourcbon2.local.LocalDocumentStore
import io.github.mohamtur1.fourcbon2.local.RecordCodec
import io.github.mohamtur1.fourcbon2.local.TimelineEvent
import io.github.mohamtur1.fourcbon2.local.TimelineEventKind
import io.github.mohamtur1.fourcbon2.local.TimelineEventStatus

/**
 * Actual action timeline (Division 27).
 *
 * Records plan/action/result/check events with honest status labels:
 * attempted, pending, manual, verified, or uncertain. An event is only
 * recorded when it actually occurred — never to imply activity that did not
 * happen.
 */
class ActionTimeline(
    private val store: LocalDocumentStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val events = mutableListOf<TimelineEvent>()

    fun load() {
        events.clear()
        events.addAll(store.readCollection("timeline") { TimelineEventCodec.decode(it) })
    }

    fun record(
        kind: TimelineEventKind,
        description: String,
        status: TimelineEventStatus,
    ): TimelineEvent {
        require(description.isNotBlank()) { "Timeline description is required." }
        val event = TimelineEvent(
            id = "event-${events.size + 1}",
            kind = kind,
            description = description,
            status = status,
            occurredAtEpochMs = clock(),
        )
        events.add(event)
        store.writeCollection("timeline", events) { TimelineEventCodec.encode(it) }
        return event
    }

    fun all(): List<TimelineEvent> = events.toList()

    fun eventsOfKind(kind: TimelineEventKind): List<TimelineEvent> =
        events.filter { it.kind == kind }

    fun latestOfKind(kind: TimelineEventKind): TimelineEvent? =
        eventsOfKind(kind).lastOrNull()

    /** The timeline must never fabricate verified results from uncertain actions. */
    fun isVerified(eventId: String): Boolean {
        val event = events.find { it.id == eventId } ?: return false
        return event.status == TimelineEventStatus.VERIFIED
    }
}

object TimelineEventCodec {
    fun encode(event: TimelineEvent): String = RecordCodec.join(
        listOf(event.id, event.kind.name, event.description, event.status.name, event.occurredAtEpochMs.toString()),
    )

    fun decode(line: String): TimelineEvent {
        val fields = RecordCodec.split(line)
        require(fields.size == 5) { "Invalid timeline event." }
        return TimelineEvent(
            id = fields[0],
            kind = TimelineEventKind.valueOf(fields[1]),
            description = fields[2],
            status = TimelineEventStatus.valueOf(fields[3]),
            occurredAtEpochMs = fields[4].toLong(),
        )
    }
}
