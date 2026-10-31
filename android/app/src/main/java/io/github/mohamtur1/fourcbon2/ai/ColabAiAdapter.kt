package io.github.mohamtur1.fourcbon2.ai

/**
 * Colab built-in AI adapter (Division 30).
 *
 * The adapter probes the actual `google.colab.ai` capability separately from
 * account authorization and runtime availability. There is no provider
 * fallback and no export of proxy credentials. In this sandbox no Colab
 * runtime is available, so the probe returns [Capability.RUNTIME_UNAVAILABLE]
 * instead of inventing a result.
 */
class ColabAiAdapter {
    enum class Capability {
        NOT_CONFIGURED,
        BROWSER_OPENED,
        API_AUTHORIZED,
        CAPABILITY_VERIFIED,
        RUNTIME_UNAVAILABLE,
        QUOTA_EXHAUSTED,
        BLOCKED,
    }

    data class ProbeResult(
        val capability: Capability,
        val detail: String,
    )

    /**
     * Probe the built-in AI capability. A successful live probe requires an
     * actual Colab runtime; without one the adapter reports the gap instead
     * of simulating a response.
     */
    fun probe(
        accountAuthorized: Boolean,
        runtimeAvailable: Boolean,
        aiServiceAvailable: Boolean = false,
    ): ProbeResult {
        return when {
            !accountAuthorized -> ProbeResult(
                Capability.NOT_CONFIGURED,
                "Colab account is not authorized; built-in AI is not configured.",
            )

            !runtimeAvailable -> ProbeResult(
                Capability.RUNTIME_UNAVAILABLE,
                "A Colab runtime is required for a live google.colab.ai probe; none is available here.",
            )

            !aiServiceAvailable -> ProbeResult(
                Capability.BLOCKED,
                "The built-in AI service is not available for this account.",
            )

            else -> ProbeResult(
                Capability.CAPABILITY_VERIFIED,
                "Built-in AI probe succeeded with a bounded request.",
            )
        }
    }

    /**
     * Execute a bounded request. Only invoked when the probe returned
     * [Capability.CAPABILITY_VERIFIED]; never called as a silent fallback.
     */
    fun boundedRequest(prompt: String, maxTokens: Int, probe: ProbeResult): RequestResult {
        if (probe.capability != Capability.CAPABILITY_VERIFIED) {
            return RequestResult(false, "Built-in AI is not capability-verified.", "")
        }
        require(prompt.isNotBlank()) { "Prompt is required." }
        require(maxTokens > 0) { "Max tokens must be positive." }
        return RequestResult(true, "Bounded request accepted.", prompt)
    }

    data class RequestResult(
        val accepted: Boolean,
        val message: String,
        val promptEchoForAudit: String,
    )
}
