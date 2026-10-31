package io.github.mohamtur1.fourcbon2.routines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineEngineTest {

    private fun routine(id: String = "r1", steps: Int = 2) =
        RoutineEngine.Routine(
            id = id,
            name = "Harmless check",
            steps = (1..steps).map { "step-$it" },
            preconditions = listOf("owner-triggered"),
        )

    @Test
    fun triggersOnlyOnExplicitHumanTrigger() {
        val engine = RoutineEngine()
        engine.register(routine())
        val outcome = engine.trigger("r1", triggerOwner = "owner", preconditionsHold = true)
        assertEquals(RoutineEngine.TriggerResult.TRIGGERED, outcome.result)
        assertEquals(2, outcome.stepsExecuted.size)
    }

    @Test
    fun missingPreconditionsBlockExecution() {
        val engine = RoutineEngine()
        engine.register(routine())
        val outcome = engine.trigger("r1", triggerOwner = "owner", preconditionsHold = false)
        assertEquals(RoutineEngine.TriggerResult.NOT_READY, outcome.result)
        assertTrue(outcome.stepsExecuted.isEmpty())
    }

    @Test
    fun missingRoutineIsReported() {
        val engine = RoutineEngine()
        val outcome = engine.trigger("missing", triggerOwner = "owner", preconditionsHold = true)
        assertEquals(RoutineEngine.TriggerResult.NOT_FOUND, outcome.result)
    }

    @Test
    fun executionIsBounded() {
        val engine = RoutineEngine()
        engine.register(routine(steps = 3))
        val first = engine.trigger("r1", triggerOwner = "owner", preconditionsHold = true)
        assertEquals(3, first.stepsExecuted.size)
        val second = engine.trigger("r1", triggerOwner = "owner", preconditionsHold = true)
        assertEquals(RoutineEngine.TriggerResult.MAX_STEPS_REACHED, second.result)
        assertEquals(3, engine.executionCount("r1"))
    }

    @Test
    fun duplicateRoutineIsRejected() {
        val engine = RoutineEngine()
        engine.register(routine())
        try {
            engine.register(routine())
            throw AssertionError("Expected duplicate rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
