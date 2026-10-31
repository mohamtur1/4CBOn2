package io.github.mohamtur1.fourcbon2.control

/**
 * Control session lease (Division 14).
 *
 * The phone holds one active control lease at a time. Take Over stops new
 * automated work and bumps the cancellation epoch; stale cloud commands
 * carrying an old epoch are rejected. Resume requires an explicit owner
 * action — it never happens silently after a crash or reboot.
 */
class TakeoverLease(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class State { IDLE, TAKEN_OVER, CANCELLED }

    private var state: State = State.IDLE
    private var owner: String? = null
    private var cancellationEpoch: Long = 0
    private var takenOverAtEpochMs: Long? = null

    fun isControlActive(): Boolean = state == State.IDLE

    fun beginControl(owner: String): Boolean {
        if (state != State.IDLE) return false
        this.owner = owner
        return true
    }

    fun takeOver(owner: String): Boolean {
        if (owner != this.owner) return false
        if (state == State.TAKEN_OVER) return false
        state = State.TAKEN_OVER
        cancellationEpoch++
        takenOverAtEpochMs = clock()
        return true
    }

    /** Cancellation invalidates the session permanently. */
    fun cancel(owner: String): Boolean {
        if (owner != this.owner) return false
        state = State.CANCELLED
        cancellationEpoch++
        takenOverAtEpochMs = null
        return true
    }

    /**
     * Stale-command guard: a command whose epoch is older than the current
     * cancellation epoch must not act. After takeover, only an explicit
     * resume with a fresh epoch is accepted.
     */
    fun acceptCommand(commandEpoch: Long, commandOwner: String): Boolean {
        if (owner != commandOwner) return false
        if (state == State.CANCELLED) return false
        if (state == State.TAKEN_OVER) return false
        if (commandEpoch < cancellationEpoch) return false
        return true
    }

    /** Explicit resume: allowed only after takeover, never after cancel. */
    fun resume(owner: String): Boolean {
        if (owner != this.owner) return false
        if (state != State.TAKEN_OVER) return false
        state = State.IDLE
        return true
    }

    fun currentEpoch(): Long = cancellationEpoch

    fun currentState(): State = state
}
