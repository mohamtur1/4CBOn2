package io.github.mohamtur1.fourcbon2.guard

/**
 * Critical phone-action guard (Division 32).
 *
 * Sensitive and manual-only contexts are never executed automatically. Every
 * critical action shows an exact preview and requires a fresh scoped
 * confirmation. There is no blind irreversible retry: an uncertain previous
 * outcome must be reconciled before any retry.
 */
class CriticalActionGuard {
    enum class SensitiveContext {
        NONE,
        PAYMENT,
        PASSWORD_OR_OTP,
        BIOMETRIC,
        PERMISSION_GRANT,
        SECURITY_SETTINGS,
        SUBMISSION,
    }

    enum class Decision { ALLOWED, MANUAL_ONLY, REJECTED }

    data class Preview(
        val actionDescription: String,
        val destination: String,
        val artifactHash: String,
    )

    data class DecisionResult(
        val decision: Decision,
        val reason: String,
    )

    fun evaluate(
        action: Preview,
        context: SensitiveContext,
        accountVerified: Boolean,
        approvalGranted: Boolean,
        previousOutcomeUncertain: Boolean = false,
    ): DecisionResult {
        if (previousOutcomeUncertain) {
            return DecisionResult(
                Decision.REJECTED,
                "Uncertain previous outcome must be reconciled before any retry.",
            )
        }
        if (context != SensitiveContext.NONE) {
            return DecisionResult(
                Decision.MANUAL_ONLY,
                "Sensitive context requires the owner to perform the action manually.",
            )
        }
        if (!accountVerified) {
            return DecisionResult(Decision.REJECTED, "Account is not verified.")
        }
        if (!approvalGranted) {
            return DecisionResult(Decision.REJECTED, "Fresh scoped approval is required.")
        }
        return DecisionResult(Decision.ALLOWED, "Preconditions and approval verified.")
    }
}
