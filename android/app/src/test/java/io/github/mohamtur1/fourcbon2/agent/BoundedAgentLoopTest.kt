package io.github.mohamtur1.fourcbon2.agent

import io.github.mohamtur1.fourcbon2.agent.BoundedAgentLoop.Outcome
import io.github.mohamtur1.fourcbon2.agent.BoundedAgentLoop.StepOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedAgentLoopTest {

    private fun step(name: String, verified: Boolean = true): BoundedAgentLoop.Step =
        BoundedAgentLoop.Step { StepOutcome(name, executed = true, verified = verified) }

    @Test
    fun completesWhenAllStepsVerify() {
        val loop = BoundedAgentLoop(maxActions = 5)
        val result = loop.run(
            goal = "inspect evaluation method",
            stopCheck = { false },
            steps = listOf(step("open page"), step("save passage"), step("verify metric")),
        )
        assertEquals(Outcome.COMPLETED, result.outcome)
        assertEquals(3, result.steps.size)
    }

    @Test
    fun ownerStopStopsBeforeNextStep() {
        val loop = BoundedAgentLoop(maxActions = 5)
        var stop = false
        val result = loop.run(
            goal = "goal",
            stopCheck = { stop },
            steps = listOf(step("a"), step("b")),
        )
        assertEquals(Outcome.COMPLETED, result.outcome)
        stop = true
        val stopped = loop.run(
            goal = "goal",
            stopCheck = { stop },
            steps = listOf(step("a"), step("b")),
        )
        assertEquals(Outcome.STOPPED, stopped.outcome)
        assertEquals(0, stopped.steps.size)
    }

    @Test
    fun actionBudgetStopsTheLoop() {
        val loop = BoundedAgentLoop(maxActions = 2)
        val result = loop.run(
            goal = "goal",
            stopCheck = { false },
            steps = listOf(step("a"), step("b"), step("c")),
        )
        assertEquals(Outcome.STOPPED, result.outcome)
        assertEquals(2, result.steps.size)
        assertTrue(result.message.contains("budget"))
    }

    @Test
    fun verificationFailureStopsWithError() {
        val loop = BoundedAgentLoop(maxActions = 5)
        val result = loop.run(
            goal = "goal",
            stopCheck = { false },
            steps = listOf(step("a"), step("b", verified = false), step("c")),
        )
        assertEquals(Outcome.ERROR, result.outcome)
        assertEquals(2, result.steps.size)
        assertFalse(result.steps[1].verified)
    }

    @Test
    fun throwingStepStopsWithError() {
        val loop = BoundedAgentLoop(maxActions = 5)
        val result = loop.run(
            goal = "goal",
            stopCheck = { false },
            steps = listOf(step("a"), BoundedAgentLoop.Step { throw IllegalStateException("boom") }, step("c")),
        )
        assertEquals(Outcome.ERROR, result.outcome)
        assertEquals(2, result.steps.size)
        assertFalse(result.steps[1].executed)
    }

    @Test
    fun blankGoalIsRejected() {
        val loop = BoundedAgentLoop(maxActions = 5)
        try {
            loop.run(goal = "", stopCheck = { false }, steps = emptyList())
            throw AssertionError("Expected blank goal rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
