package io.github.mohamtur1.fourcbon2.agent

/**
 * One bounded agent loop (Division 31).
 *
 * Goal -> one permitted action -> verification. The loop enforces an action
 * budget, checks the owner stop flag before every step, stops cleanly on
 * verification failure, and never records uncertain outcomes as verified.
 */
class BoundedAgentLoop(
    private val maxActions: Int = 5,
) {
    enum class Outcome { COMPLETED, STOPPED, ERROR }

    data class StepOutcome(
        val name: String,
        val executed: Boolean,
        val verified: Boolean,
    )

    data class LoopResult(
        val outcome: Outcome,
        val steps: List<StepOutcome>,
        val message: String,
    )

    fun interface Step {
        fun execute(): StepOutcome
    }

    fun run(
        goal: String,
        stopCheck: () -> Boolean,
        steps: List<Step>,
    ): LoopResult {
        require(goal.isNotBlank()) { "Goal is required." }
        require(maxActions > 0) { "Action budget must be positive." }
        val executed = mutableListOf<StepOutcome>()

        for (step in steps) {
            if (stopCheck()) {
                return LoopResult(Outcome.STOPPED, executed.toList(), "Owner stop requested before '${step.name}'.")
            }
            if (executed.size >= maxActions) {
                return LoopResult(Outcome.STOPPED, executed.toList(), "Action budget reached.")
            }
            val outcome = try {
                step.execute()
            } catch (e: Exception) {
                executed.add(StepOutcome(step.name, executed = false, verified = false))
                return LoopResult(Outcome.ERROR, executed.toList(), "Step '${step.name}' failed: ${e.message}")
            }
            executed.add(outcome)
            if (!outcome.verified) {
                return LoopResult(Outcome.ERROR, executed.toList(), "Verification failed for step '${outcome.name}'.")
            }
        }
        return LoopResult(Outcome.COMPLETED, executed.toList(), "Goal completed within budget.")
    }
}
