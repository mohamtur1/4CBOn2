package io.github.mohamtur1.fourcbon2.browser

/**
 * Controlled browser workspace policy (Division 25).
 *
 * URL boundaries for the agent browser workspace: HTTPS-only, host
 * allow-list, blocked private/metadata destinations, and no privileged
 * native bridge to untrusted content.
 */
class BrowserWorkspacePolicy(
    private val allowedHosts: List<String>,
) {
    private val blockedHosts = listOf("metadata.google.internal", "169.254.169.254", "localhost", "127.0.0.1")

    fun isPermitted(host: String): Boolean {
        if (host.isBlank()) return false
        if (blockedHosts.contains(host)) return false
        return allowedHosts.contains(host)
    }

    /** True when navigation to [url] is allowed by this policy. */
    fun allowsNavigation(url: String): Boolean {
        return try {
            val parsed = java.net.URI(url)
            parsed.scheme == "https" && isPermitted(parsed.host ?: "")
        } catch (e: Exception) {
            false
        }
    }

    /** No privileged native bridge is ever exposed to page content. */
    fun allowsNativeBridge(): Boolean = false
}
