package io.github.mohamtur1.fourcbon2.approval

import io.github.mohamtur1.fourcbon2.local.ApprovalRequest
import io.github.mohamtur1.fourcbon2.local.ApprovalState

/**
 * Sensitive-action approval gate (Division 20).
 *
 * A sensitive action requires a scoped destination and artifact identity,
 * an expiry, and a per-request action cap. Grants are consumed per action
 * and never reused; expired or cancelled requests are rejected.
 */
class ApprovalGate(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val requests = mutableListOf<ApprovalRequest>()

    fun request(
        actionDescription: String,
        destination: String,
        artifactHash: String,
        expiryEpochMs: Long,
        maxActions: Int,
    ): ApprovalRequest {
        require(actionDescription.isNotBlank()) { "Action description is required." }
        require(destination.isNotBlank()) { "Destination is required." }
        require(artifactHash.isNotBlank()) { "Artifact hash is required." }
        require(maxActions > 0) { "Max actions must be positive." }
        require(expiryEpochMs > clock()) { "Expiry must be in the future." }
        val request = ApprovalRequest(
            id = "approval-${requests.size + 1}",
            actionDescription = actionDescription,
            destination = destination,
            artifactHash = artifactHash,
            expiryEpochMs = expiryEpochMs,
            maxActions = maxActions,
            state = ApprovalState.PENDING,
        )
        requests.add(request)
        return request
    }

    fun grant(id: String): ApprovalRequest? {
        val request = requests.find { it.id == id } ?: return null
        require(request.state == ApprovalState.PENDING) { "Only pending requests can be granted." }
        val granted = request.copy(state = ApprovalState.GRANTED)
        requests.remove(request)
        requests.add(granted)
        return granted
    }

    fun deny(id: String): ApprovalRequest? {
        val request = requests.find { it.id == id } ?: return null
        require(request.state == ApprovalState.PENDING) { "Only pending requests can be denied." }
        val denied = request.copy(state = ApprovalState.DENIED)
        requests.remove(request)
        requests.add(denied)
        return denied
    }

    fun cancel(id: String): ApprovalRequest? {
        val request = requests.find { it.id == id } ?: return null
        if (request.state == ApprovalState.DENIED) return null
        val cancelled = request.copy(state = ApprovalState.CANCELLED)
        requests.remove(request)
        requests.add(cancelled)
        return cancelled
    }

    /**
     * Consume one action under a grant. Rejects expired grants, cancelled
     * grants, and grants whose action cap is exhausted. A consumed grant is
     * removed — it is never reused.
     */
    fun consume(id: String, destination: String, artifactHash: String): Boolean {
        val request = requests.find { it.id == id } ?: return false
        if (request.state != ApprovalState.GRANTED) return false
        if (clock() > request.expiryEpochMs) {
            val expired = request.copy(state = ApprovalState.EXPIRED)
            requests.remove(request)
            requests.add(expired)
            return false
        }
        if (request.destination != destination) return false
        if (request.artifactHash != artifactHash) return false
        val remaining = request.maxActions - 1
        if (remaining <= 0) {
            val expired = request.copy(state = ApprovalState.EXPIRED)
            requests.remove(request)
            requests.add(expired)
            return false
        }
        val updated = request.copy(maxActions = remaining)
        requests.remove(request)
        requests.add(updated)
        return true
    }

    fun state(id: String): ApprovalState? = requests.find { it.id == id }?.state
}
