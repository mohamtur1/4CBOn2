package io.github.mohamtur1.fourcbon2.browser

import java.net.URI

/**
 * Chrome-backed opening (Division 24).
 *
 * Decision logic for opening permitted URLs through Custom Tabs (or an
 * explicit external-browser fallback). The actual CustomTabs launch is thin
 * Android glue; this class decides what may be opened and when a fallback is
 * required. No unrestricted Chrome-control claim is made.
 */
class CustomTabOpener {
    enum class Outcome { OPEN_IN_CUSTOM_TAB, FALLBACK_TO_EXTERNAL_BROWSER, REJECTED }

    data class OpenDecision(
        val outcome: Outcome,
        val reason: String,
    )

    fun decide(
        url: String,
        chromeAvailable: Boolean,
        policy: BrowserWorkspacePolicy,
    ): OpenDecision {
        val parsed = try {
            URI(url)
        } catch (e: Exception) {
            return OpenDecision(Outcome.REJECTED, "Invalid URL.")
        }
        if (parsed.scheme != "https") {
            return OpenDecision(Outcome.REJECTED, "Only HTTPS URLs may be opened.")
        }
        if (!policy.isPermitted(parsed.host ?: "")) {
            return OpenDecision(Outcome.REJECTED, "Host is not permitted: ${parsed.host}")
        }
        if (!chromeAvailable) {
            return OpenDecision(Outcome.FALLBACK_TO_EXTERNAL_BROWSER, "Chrome is not available; opening with the supported browser fallback.")
        }
        return OpenDecision(Outcome.OPEN_IN_CUSTOM_TAB, "Opening in a Chrome-backed Custom Tab.")
    }
}
