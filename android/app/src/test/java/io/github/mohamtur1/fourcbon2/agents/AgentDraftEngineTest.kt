package io.github.mohamtur1.fourcbon2.agents

import io.github.mohamtur1.fourcbon2.local.DraftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AgentDraftEngineTest {

    @Test
    fun draftLifecycle() {
        val engine = AgentDraftEngine()
        val draft = engine.create("Write report", "Summarize the report", "report-template", budget = 5)
        assertEquals(DraftState.DRAFT, draft.state)

        engine.instantiateTemplate(draft.id)
        assertEquals(DraftState.TEMPLATE_INSTANTIATED, engine.load(draft.id)?.state)

        engine.markReadyToQueue(draft.id)
        assertEquals(DraftState.READY_TO_QUEUE, engine.load(draft.id)?.state)

        engine.markQueued(draft.id)
        assertEquals(DraftState.QUEUED, engine.load(draft.id)?.state)
    }

    @Test
    fun queuedDraftCannotBeEdited() {
        val engine = AgentDraftEngine()
        val draft = engine.create("A", "B", "template", budget = 1)
        engine.instantiateTemplate(draft.id)
        engine.markReadyToQueue(draft.id)
        engine.markQueued(draft.id)
        assertNull(engine.update(draft.id, "new title", "new goal"))
    }

    @Test
    fun onlyInstantiatedDraftsCanBeMarkedReady() {
        val engine = AgentDraftEngine()
        val draft = engine.create("A", "B", "template", budget = 1)
        assertNull(engine.markReadyToQueue(draft.id))
    }

    @Test
    fun queuedDraftCannotBeCancelled() {
        val engine = AgentDraftEngine()
        val draft = engine.create("A", "B", "template", budget = 1)
        engine.instantiateTemplate(draft.id)
        engine.markReadyToQueue(draft.id)
        engine.markQueued(draft.id)
        assertNull(engine.cancel(draft.id))
    }

    @Test
    fun invalidDraftInputsAreRejected() {
        val engine = AgentDraftEngine()
        try {
            engine.create("", "B", "template", budget = 1)
            throw AssertionError("Expected blank title rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            engine.create("A", "B", "template", budget = 0)
            throw AssertionError("Expected budget rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun loadAndAllWork() {
        val engine = AgentDraftEngine()
        val draft = engine.create("A", "B", "template", budget = 1)
        assertNotNull(engine.load(draft.id))
        assertEquals(1, engine.all().size)
        assertNull(engine.load("missing"))
    }
}
