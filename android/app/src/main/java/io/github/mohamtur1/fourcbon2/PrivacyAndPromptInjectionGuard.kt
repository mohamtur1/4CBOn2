package io.github.mohamtur1.fourcbon2

import java.util.Locale

/**
 * Division 35: Privacy, Untrusted-Input Prompt-Injection Defense, and Exfiltration Guard.
 *
 * Ensures untrusted page content, imported documents, and external messages remain inert data:
 * - Detects and neutralizes prompt-injection directives attempting to override scope, grant
 *   permissions, redirect outbound destinations, or exfiltrate local secrets/screen content.
 * - Blocks outbound requests whose URLs, headers, or payloads contain secret markers, sensitive
 *   screen observations, or unauthorized destinations outside the task's approved scope.
 */
data class UntrustedInputInspection(
    val sourceLabel: String,
    val sanitizedDataEnvelope: String,
    val injectionSignalsDetected: List<String>,
    val containsRedactedSecrets: Boolean,
) {
    val isSuspicious: Boolean
        get() = injectionSignalsDetected.isNotEmpty()
}

sealed interface OutboundDataCheckResult {
    data class Permitted(val sanitizedPayload: String) : OutboundDataCheckResult
    data class BlockedExfiltrationOrScopeEscape(val reason: String) : OutboundDataCheckResult
}

class PrivacyAndPromptInjectionGuard(
    private val observationBoundary: ConsentedObservationBoundary,
) {
    /**
     * Wraps untrusted text (web page, email/message body, imported CSV/JSON cell) in an explicit
     * inert-data boundary, redacts any accidental secret tokens, and records prompt-injection
     * patterns so callers never treat untrusted content as authority or tool instructions.
     */
    fun inspectAndIsolateUntrustedContent(
        sourceLabel: String,
        rawUntrustedText: String,
    ): UntrustedInputInspection {
        val redacted = ConsentedObservationBoundary.redactSecrets(rawUntrustedText)
        val secretsFound = redacted != rawUntrustedText

        val detected = INJECTION_RULES
            .filter { (_, regex) -> regex.containsMatchIn(rawUntrustedText) }
            .map { (name, _) -> name }

        val neutralizedBody = redacted
            .replace("</untrusted_data>", "[escaped_tag]", ignoreCase = true)
            .take(MAX_ISOLATED_CHARS)

        val envelope = buildString {
            appendLine("<untrusted_data source=\"${sourceLabel.replace('"', '_')}\" authority=\"none\">")
            appendLine(neutralizedBody)
            append("</untrusted_data>")
        }

        return UntrustedInputInspection(
            sourceLabel = sourceLabel,
            sanitizedDataEnvelope = envelope,
            injectionSignalsDetected = detected,
            containsRedactedSecrets = secretsFound,
        )
    }

    /**
     * Enforces destination allowlists, cloud-sharing scope, sensitive-screen exclusions, and
     * query-string/payload secret-leak prevention before any data leaves the device.
     */
    fun verifyOutboundTransfer(
        destinationUrlOrTarget: String,
        allowedDestinations: Set<String>,
        payload: String,
        sourceSensitivity: ScreenSensitivity = ScreenSensitivity.NORMAL_APP_SURFACE,
        isCloudTransfer: Boolean = true,
        scopeAllowsCloudTransfer: Boolean = false,
    ): OutboundDataCheckResult {
        if (sourceSensitivity != ScreenSensitivity.NORMAL_APP_SURFACE) {
            return OutboundDataCheckResult.BlockedExfiltrationOrScopeEscape(
                "Blocked outbound transfer from sensitive screen context (${sourceSensitivity.name}).",
            )
        }

        val normalizedDest = destinationUrlOrTarget.trim()
        if (normalizedDest.isEmpty()) {
            return OutboundDataCheckResult.BlockedExfiltrationOrScopeEscape("Destination must not be blank.")
        }

        // Check destination against explicit owner-approved destinations
        val destinationPermitted = allowedDestinations.any { allowed ->
            val cleanAllowed = allowed.trim().lowercase(Locale.ROOT)
            val candidateLower = normalizedDest.lowercase(Locale.ROOT)
            candidateLower == cleanAllowed ||
                (cleanAllowed.startsWith("https://") && candidateLower.startsWith(cleanAllowed))
        }
        if (!destinationPermitted) {
            return OutboundDataCheckResult.BlockedExfiltrationOrScopeEscape(
                "Unauthorized destination '$normalizedDest' is outside approved scope.",
            )
        }

        // Block secret smuggling in URL query strings or path segments
        if (SharedJobBundleManager.containsForbiddenSecretMarkers(normalizedDest) ||
            SENSITIVE_QUERY_PARAM_REGEX.containsMatchIn(normalizedDest)
        ) {
            return OutboundDataCheckResult.BlockedExfiltrationOrScopeEscape(
                "Blocked potential credential/secret leakage in destination URL.",
            )
        }

        // Enforce cloud-sharing scope contract + observation policy
        if (isCloudTransfer) {
            if (!scopeAllowsCloudTransfer) {
                return OutboundDataCheckResult.BlockedExfiltrationOrScopeEscape(
                    "Task scope prohibits cloud data transfer.",
                )
            }
            if (!observationBoundary.currentPolicy().allowCloudObservationUpload) {
                return OutboundDataCheckResult.BlockedExfiltrationOrScopeEscape(
                    "Observation policy has Cloud Upload set to Off.",
                )
            }
        }

        // Block raw secrets in outbound payload
        if (SharedJobBundleManager.containsForbiddenSecretMarkers(payload)) {
            return OutboundDataCheckResult.BlockedExfiltrationOrScopeEscape(
                "Blocked outbound payload containing raw credential or Colab proxy token.",
            )
        }

        return OutboundDataCheckResult.Permitted(ConsentedObservationBoundary.redactSecrets(payload))
    }

    companion object {
        private const val MAX_ISOLATED_CHARS = 16_000

        private val SENSITIVE_QUERY_PARAM_REGEX = Regex(
            "(?i)[?&](token|secret|password|api_key|pat|key|otp|auth)=",
        )

        private val INJECTION_RULES: List<Pair<String, Regex>> = listOf(
            "instruction_override" to Regex("(?i)\\b(ignore|disregard|override)\\s+(all\\s+)?(previous|prior|system|safety)\\s+(instructions|rules|scope)"),
            "authority_escalation" to Regex("(?i)\\b(grant\\s+permission|enable\\s+all\\s+apps|approved\\s*:\\s*true|bypass\\s+approval)"),
            "exfiltration_directive" to Regex("(?i)\\b(send|upload|forward|post|email)\\s+.*\\b(token|password|pat|credential|keystore|otp|secret)s?\\b"),
            "hidden_system_role" to Regex("(?i)(\\[\\s*system\\s*\\]|<\\s*system\\s*>|###\\s*system\\s*instruction)"),
        )
    }
}
