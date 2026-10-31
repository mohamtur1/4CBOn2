package io.github.mohamtur1.fourcbon2.routines

/**
 * Human-defined routine engine (Division 33).
 *
 * A fixed, owner-authored script with an explicit human trigger, known
 * preconditions, and bounded execution. This is not an exemption for
 * AI-driven Accessibility use, and it never executes consequential steps
 * without the normal approval gates.
 */
class RoutineEngine {
    data class Routine(
        val id: String,
        val name: String,
        val steps: List<String>,
        val preconditions: List<String>,
    ) {
        init {
            require(id.isNotBlank()) { "Routine id is required." }
            require(name.isNotBlank()) { "Routine name is required." }
            require(steps.isNotEmpty()) { "A routine needs at least one step." }
        }
    }

    enum class TriggerResult { TRIGGERED, NOT_READY, MAX_STEPS_REACHED, NOT_FOUND }

    data class TriggerOutcome(
        val result: TriggerResult,
        val stepsExecuted: List<String>,
        val reason: String,
    )

    private val routines = mutableListOf<Routine>()
    private val executedSteps = mutableMapOf<String, Int>()

    fun register(routine: Routine) {
        require(routines.none { it.id == routine.id }) { "Duplicate routine id: ${routine.id}" }
        routines.add(routine)
    }

    /**
     * Execute a routine only on an explicit human trigger. Preconditions must
     * all hold; execution is bounded by [MAX_STEPS].
     */
    fun trigger(id: String, triggerOwner: String, preconditionsHold: Boolean): TriggerOutcome {
        require(triggerOwner.isNotBlank()) { "Trigger owner is required." }
        val routine = routines.find { it.id == id } ?: return TriggerOutcome(NOT_FOUND, emptyList(), "Routine not found.")
        if (!preconditionsHold) {
            return TriggerOutcome(NOT_READY, emptyList(), "Preconditions do not hold.")
        }
        val previous = executedSteps[id] ?: 0
        if (previous >= MAX_STEPS) {
            return TriggerOutcome(MAX_STEPS_REACHED, emptyList(), "Routine execution budget reached.")
        }
        val stepsToRun = routine.steps.take(MAX_STEPS - previous)
        val executed = stepsToRun.toList()
        executedSteps[id] = previous + executed.size
        return TriggerOutcome(TRIGGERED, executed, "Routine executed ${executed.size} step(s).")
    }

    fun executionCount(id: String): Int = executedSteps[id] ?: 0

    companion object {
        const val MAX_STEPS = 10
    }
}
