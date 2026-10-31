package io.github.mohamtur1.fourcbon2.submission

/**
 * Submission gate and receipt states (Division 38).
 *
 * Durable intent, limits, and acknowledgment/uncertainty handling. Live
 * submission is separately authorized: this class models the full state
 * machine and is exercised by mocked tests; the actual HTTP submission call
 * is out of scope until the owner authorizes it.
 */
class SubmissionGate(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class State { DRAFT, INTENT_SAVED, WAITING_FOR_APPROVAL, AUTHORIZED, SUBMITTED, UNCERTAIN_EXTERNAL_OUTCOME, CANCELLED }

    enum class Acknowledgment { ACKNOWLEDGED, UNCERTAIN, REJECTED }

    data class SubmissionIntent(
        val id: String,
        val competitionId: String,
        val artifactHash: String,
        val deadlineEpochMs: Long,
        val state: State,
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() > deadlineEpochMs
    }

    data class SubmissionOutcome(
        val accepted: Boolean,
        val state: State,
        val message: String,
    )

    private val intents = mutableListOf<SubmissionIntent>()

    fun saveIntent(competitionId: String, artifactHash: String, deadlineEpochMs: Long): SubmissionIntent {
        require(competitionId.isNotBlank()) { "Competition id is required." }
        require(artifactHash.isNotBlank()) { "Artifact hash is required." }
        require(deadlineEpochMs > clock()) { "Deadline must be in the future." }
        val intent = SubmissionIntent(
            id = "submission-${intents.size + 1}",
            competitionId = competitionId,
            artifactHash = artifactHash,
            deadlineEpochMs = deadlineEpochMs,
            state = State.INTENT_SAVED,
        )
        intents.add(intent)
        return intent
    }

    fun requestAuthorization(id: String): SubmissionIntent? {
        val intent = intents.find { it.id == id } ?: return null
        require(intent.state == State.INTENT_SAVED) { "Only saved intents can request authorization." }
        return transition(intent, State.WAITING_FOR_APPROVAL)
    }

    fun authorize(id: String): SubmissionIntent? {
        val intent = intents.find { it.id == id } ?: return null
        require(intent.state == State.WAITING_FOR_APPROVAL) { "Only pending authorizations can be granted." }
        return transition(intent, State.AUTHORIZED)
    }

    fun deny(id: String): SubmissionIntent? {
        val intent = intents.find { it.id == id } ?: return null
        require(intent.state == State.WAITING_FOR_APPROVAL) { "Only pending authorizations can be denied." }
        return transition(intent, State.CANCELLED)
    }

    /**
     * Mocked submission. Acknowledgment handling is explicit:
     * ACKNOWLEDGED → SUBMITTED; UNCERTAIN → UNCERTAIN_EXTERNAL_OUTCOME
     * (never silently retried); REJECTED → remains AUTHORIZED for owner review.
     */
    fun submit(id: String, acknowledgment: Acknowledgment): SubmissionOutcome {
        val intent = intents.find { it.id == id } ?: return SubmissionOutcome(false, State.DRAFT, "Intent not found.")
        if (intent.state != State.AUTHORIZED) {
            return SubmissionOutcome(false, intent.state, "Intent is not authorized.")
        }
        if (intent.isExpired) {
            return SubmissionOutcome(false, intent.state, "Intent deadline has passed.")
        }
        return when (acknowledgment) {
            Acknowledgment.ACKNOWLEDGED -> {
                transition(intent, State.SUBMITTED)
                SubmissionOutcome(true, State.SUBMITTED, "Submission acknowledged.")
            }

            Acknowledgment.UNCERTAIN -> {
                transition(intent, State.UNCERTAIN_EXTERNAL_OUTCOME)
                SubmissionOutcome(false, State.UNCERTAIN_EXTERNAL_OUTCOME, "Submission outcome is uncertain; reconcile before any retry.")
            }

            Acknowledgment.REJECTED ->
                SubmissionOutcome(false, intent.state, "Submission rejected; intent remains authorized for owner review.")
        }
    }

    fun reconcileUncertain(id: String): SubmissionIntent? {
        val intent = intents.find { it.id == id } ?: return null
        require(intent.state == State.UNCERTAIN_EXTERNAL_OUTCOME) { "Only uncertain outcomes can be reconciled." }
        return transition(intent, State.SUBMITTED)
    }

    fun cancel(id: String): SubmissionIntent? {
        val intent = intents.find { it.id == id } ?: return null
        require(intent.state != State.SUBMITTED) { "Submitted intents cannot be cancelled." }
        return transition(intent, State.CANCELLED)
    }

    fun state(id: String): State? = intents.find { it.id == id }?.state

    private fun transition(intent: SubmissionIntent, newState: State): SubmissionIntent {
        val updated = intent.copy(state = newState)
        intents.remove(intent)
        intents.add(updated)
        return updated
    }
}
