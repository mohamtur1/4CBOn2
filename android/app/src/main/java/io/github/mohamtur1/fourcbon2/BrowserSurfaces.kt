package io.github.mohamtur1.fourcbon2

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

/**
 * Divisions 24 & 25:
 * - Division 24: Chrome-backed opening — launches approved HTTPS service links (Colab, GitHub,
 *   Kaggle, OAuth sign-in) in Chrome Custom Tabs with browser fallback; never claims Custom Tab
 *   open grants programmatic Chrome tab control.
 * - Division 25: Controlled browser workspace — scoped public-page reader workflow with strict
 *   HTTPS/host/redirect boundaries, auth-flow redirection to Custom Tabs, and zero privileged
 *   `addJavascriptInterface` bridges to untrusted content.
 */
sealed interface UrlValidationResult {
    data class Valid(val normalizedUri: URI, val hostLower: String) : UrlValidationResult
    data class Rejected(val reason: String) : UrlValidationResult
}

object ControlledUrlValidator {
    fun validatePublicHttpsUrl(rawUrl: String): UrlValidationResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return UrlValidationResult.Rejected("URL must not be empty.")
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return UrlValidationResult.Rejected("Malformed URL syntax.")
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
            ?: return UrlValidationResult.Rejected("URL must specify https:// scheme.")
        if (scheme != "https") {
            return UrlValidationResult.Rejected("Only HTTPS URLs are permitted (rejected '$scheme').")
        }
        if (uri.userInfo != null) {
            return UrlValidationResult.Rejected("Embedded credentials in URLs are prohibited.")
        }

        val host = uri.host?.lowercase(Locale.ROOT)?.trim('[', ']')
            ?: return UrlValidationResult.Rejected("URL must contain a valid hostname.")
        if (host.isBlank() || isBlockedLocalOrMetadataHost(host)) {
            return UrlValidationResult.Rejected("Localhost, private-network, and metadata hosts are blocked.")
        }

        return UrlValidationResult.Valid(uri, host)
    }

    fun isInteractiveSignInPath(uri: URI): Boolean {
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val path = uri.path?.lowercase(Locale.ROOT).orEmpty()
        if (host == "accounts.google.com" || host == "colab.research.google.com") return true
        return SIGN_IN_PATH_SEGMENTS.any { seg -> path.contains(seg) }
    }

    private fun isBlockedLocalOrMetadataHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
            host == "metadata.google.internal" || host == "169.254.169.254" ||
            host == "::1" || host == "0:0:0:0:0:0:0:1" || host == "0.0.0.0"
        ) {
            return true
        }
        if (host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")) {
            return true
        }
        val ipv4Parts = host.split('.')
        if (ipv4Parts.size == 4 && ipv4Parts.all { it.toIntOrNull() in 0..255 }) {
            val a = ipv4Parts[0].toInt()
            val b = ipv4Parts[1].toInt()
            if (a == 127 || a == 10 || a == 0) return true
            if (a == 169 && b == 254) return true
            if (a == 192 && b == 168) return true
            if (a == 172 && b in 16..31) return true
            if (a == 100 && b in 64..127) return true
        }
        return false
    }

    private val SIGN_IN_PATH_SEGMENTS = listOf(
        "/login",
        "/signin",
        "/sign-in",
        "/oauth",
        "/authorize",
        "/session",
        "/account/login",
    )
}

// --- Division 24: Chrome-Backed Custom Tab / External Browser Opener ---

enum class BrowserOpenMode {
    CHROME_CUSTOM_TAB,
    DEFAULT_BROWSER_FALLBACK,
}

sealed interface ChromeOpenOutcome {
    data class OpenedManualSession(
        val url: String,
        val mode: BrowserOpenMode,
        val disclaimer: String = "Browser opened for owner interaction; 4CBOn2 has no cookie/DOM access or remote tab control.",
    ) : ChromeOpenOutcome

    data class RejectedUrl(val reason: String) : ChromeOpenOutcome
    data object NoBrowserAvailable : ChromeOpenOutcome
}

interface CustomTabPlatformDriver {
    fun openHttpsCustomTab(url: String, preferChromePackage: Boolean): BrowserOpenMode?
}

class ChromeCustomTabLauncher(
    private val driver: CustomTabPlatformDriver,
    private val timeline: ActionTimelineRepository,
) {
    fun openServiceLink(
        url: String,
        label: String = "External Service Link",
        jobId: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ChromeOpenOutcome {
        val check = ControlledUrlValidator.validatePublicHttpsUrl(url)
        if (check is UrlValidationResult.Rejected) {
            timeline.recordEvent(
                stage = TimelineStage.ACTION,
                actor = TimelineActor.BROWSER_WORKSPACE,
                summary = "Rejected Custom Tab open ($label): ${check.reason}",
                outcome = TimelineOutcomeState.BLOCKED,
                jobId = jobId,
                targetReference = url,
                nowEpochMs = nowEpochMs,
            )
            return ChromeOpenOutcome.RejectedUrl(check.reason)
        }
        val normalized = (check as UrlValidationResult.Valid).normalizedUri.toString()
        val mode = driver.openHttpsCustomTab(normalized, preferChromePackage = true)
        return if (mode != null) {
            timeline.recordEvent(
                stage = TimelineStage.ACTION,
                actor = TimelineActor.BROWSER_WORKSPACE,
                summary = "Opened $label in ${mode.name}; manual browser session active.",
                outcome = TimelineOutcomeState.MANUAL_HANDOFF,
                jobId = jobId,
                targetReference = normalized,
                nowEpochMs = nowEpochMs,
            )
            ChromeOpenOutcome.OpenedManualSession(url = normalized, mode = mode)
        } else {
            timeline.recordEvent(
                stage = TimelineStage.ACTION,
                actor = TimelineActor.BROWSER_WORKSPACE,
                summary = "Failed to open $label: no browser installed.",
                outcome = TimelineOutcomeState.FAILED,
                jobId = jobId,
                targetReference = normalized,
                nowEpochMs = nowEpochMs,
            )
            ChromeOpenOutcome.NoBrowserAvailable
        }
    }

    fun openColabNotebook(nowEpochMs: Long = System.currentTimeMillis()): ChromeOpenOutcome =
        openServiceLink(
            url = ColabNotebookReference.colabOpenUrl,
            label = "Colab (${ColabNotebookReference.notebookFileName})",
            nowEpochMs = nowEpochMs,
        )
}

// --- Division 25: Controlled Browser Workspace (Reviewed Public-Page Reader) ---

data class PublicPageReaderSnapshot(
    val url: String,
    val title: String,
    val extractedText: String,
    val contentSha256: String,
    val capturedAtEpochMs: Long,
)

sealed interface ControlledReaderOutcome {
    data class ReadSuccess(val snapshot: PublicPageReaderSnapshot, val savedKnowledgeEntry: KnowledgeEntry?) :
        ControlledReaderOutcome
    data class RedirectToCustomTabRequired(val url: String, val reason: String) : ControlledReaderOutcome
    data class BlockedUrlOrDomain(val reason: String) : ControlledReaderOutcome
    data class FetchFailed(val reason: String) : ControlledReaderOutcome
}

class ControlledBrowserWorkspace(
    private val allowedDomains: Set<String> = DEFAULT_ALLOWED_PUBLIC_DOMAINS,
    private val timeline: ActionTimelineRepository,
    private val observationBoundary: ConsentedObservationBoundary,
    private val knowledgeLibrary: OfflineKnowledgeLibrary,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) {
    /**
     * Checks whether a URL is allowed inside the isolated Controlled Browser Workspace,
     * must be handed off to Chrome Custom Tabs (e.g., OAuth/login/Colab interactive session),
     * or must be blocked outright.
     */
    fun checkNavigationPolicy(rawUrl: String): ControlledNavigationDecision {
        val validated = ControlledUrlValidator.validatePublicHttpsUrl(rawUrl)
        if (validated is UrlValidationResult.Rejected) {
            return ControlledNavigationDecision.Block(validated.reason)
        }
        val valid = validated as UrlValidationResult.Valid
        if (ControlledUrlValidator.isInteractiveSignInPath(valid.normalizedUri)) {
            return ControlledNavigationDecision.UseCustomTabInstead(
                url = valid.normalizedUri.toString(),
                reason = "Authentication and interactive Colab sessions must open in Chrome Custom Tabs, never in an embedded WebView.",
            )
        }
        val hostAllowed = allowedDomains.any { domain ->
            val d = domain.lowercase(Locale.ROOT)
            valid.hostLower == d || valid.hostLower.endsWith(".$d")
        }
        if (!hostAllowed) {
            return ControlledNavigationDecision.Block(
                "Host '${valid.hostLower}' is outside the Controlled Browser Workspace allowlist.",
            )
        }
        return ControlledNavigationDecision.AllowInWorkspace(valid.normalizedUri.toString())
    }

    /**
     * Reviewed public-page reader workflow: fetches a permitted public HTTPS document without
     * cookies/credentials, strips scripts/styles, passes the Consented Observation Boundary,
     * optionally saves the snapshot into Offline Knowledge Library, and logs real timeline steps.
     */
    fun readPublicPage(
        rawUrl: String,
        saveToKnowledge: Boolean = true,
        jobId: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ControlledReaderOutcome {
        when (val decision = checkNavigationPolicy(rawUrl)) {
            is ControlledNavigationDecision.Block -> {
                timeline.recordEvent(
                    stage = TimelineStage.APPROVAL_CHECK,
                    actor = TimelineActor.BROWSER_WORKSPACE,
                    summary = "Blocked reader URL: ${decision.reason}",
                    outcome = TimelineOutcomeState.BLOCKED,
                    jobId = jobId,
                    targetReference = rawUrl,
                    nowEpochMs = nowEpochMs,
                )
                return ControlledReaderOutcome.BlockedUrlOrDomain(decision.reason)
            }
            is ControlledNavigationDecision.UseCustomTabInstead -> {
                timeline.recordEvent(
                    stage = TimelineStage.APPROVAL_CHECK,
                    actor = TimelineActor.BROWSER_WORKSPACE,
                    summary = decision.reason,
                    outcome = TimelineOutcomeState.MANUAL_HANDOFF,
                    jobId = jobId,
                    targetReference = decision.url,
                    nowEpochMs = nowEpochMs,
                )
                return ControlledReaderOutcome.RedirectToCustomTabRequired(decision.url, decision.reason)
            }
            is ControlledNavigationDecision.AllowInWorkspace -> {
                return fetchAndExtractPublicPage(decision.normalizedUrl, saveToKnowledge, jobId, nowEpochMs)
            }
        }
    }

    private fun fetchAndExtractPublicPage(
        normalizedUrl: String,
        saveToKnowledge: Boolean,
        jobId: String?,
        nowEpochMs: Long,
    ): ControlledReaderOutcome {
        val rawBody: String
        try {
            val conn = connectionFactory(URL(normalizedUrl))
            try {
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false // Reject silent cross-origin redirects!
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.useCaches = false
                conn.setRequestProperty("Accept", "text/html, text/markdown, text/plain;q=0.9")

                val status = conn.responseCode
                if (status in 300..399) {
                    val loc = conn.getHeaderField("Location").orEmpty()
                    return ControlledReaderOutcome.BlockedUrlOrDomain(
                        "Redirect (HTTP $status -> '$loc') blocked until target URL passes policy.",
                    )
                }
                if (status != 200) {
                    return ControlledReaderOutcome.FetchFailed("Public page returned HTTP $status.")
                }
                val bytes = conn.inputStream.use { stream ->
                    val buf = ByteArray(8192)
                    val out = java.io.ByteArrayOutputStream()
                    var total = 0
                    while (true) {
                        val r = stream.read(buf)
                        if (r < 0) break
                        total += r
                        if (total > MAX_READER_BYTES) {
                            return ControlledReaderOutcome.FetchFailed("Page exceeds 512 KiB reader limit.")
                        }
                        out.write(buf, 0, r)
                    }
                    out.toByteArray()
                }
                rawBody = bytes.toString(Charsets.UTF_8)
            } finally {
                conn.disconnect()
            }
        } catch (ex: IOException) {
            return ControlledReaderOutcome.FetchFailed(ex.message ?: "Network error reading public page.")
        }

        val (title, readable) = extractReadableTitleAndText(rawBody, normalizedUrl)
        val observed = observationBoundary.observe(
            channel = ObservationChannel.APPROVED_PUBLIC_PAGE_TEXT,
            rawText = readable,
            sensitivity = ScreenSensitivity.NORMAL_APP_SURFACE,
            requestCloudUpload = false,
        )
        if (observed is ObservationOutcome.Blocked) {
            return ControlledReaderOutcome.BlockedUrlOrDomain(observed.reason)
        }
        val cleanText = (observed as ObservationOutcome.Captured).redactedContent
        val hash = ScopedWorkspaceFileManager.sha256Hex(cleanText.toByteArray(Charsets.UTF_8))
        val snapshot = PublicPageReaderSnapshot(
            url = normalizedUrl,
            title = title,
            extractedText = cleanText,
            contentSha256 = hash,
            capturedAtEpochMs = nowEpochMs,
        )

        val savedEntry = if (saveToKnowledge && cleanText.isNotBlank()) {
            knowledgeLibrary.saveEntry(
                title = title,
                body = cleanText.take(8_000),
                provenance = KnowledgeProvenance.PUBLIC_PAGE_SNAPSHOT,
                sourceUrl = normalizedUrl,
                tags = listOf("browser-reader", "public-page"),
                nowEpochMs = nowEpochMs,
            )
        } else {
            null
        }

        timeline.recordEvent(
            stage = TimelineStage.OBSERVATION,
            actor = TimelineActor.BROWSER_WORKSPACE,
            summary = "Captured public reader passage '$title' (${cleanText.length} chars, SHA-256 ${hash.take(10)}…).",
            outcome = TimelineOutcomeState.VERIFIED,
            jobId = jobId,
            targetReference = normalizedUrl,
            nowEpochMs = nowEpochMs,
        )
        return ControlledReaderOutcome.ReadSuccess(snapshot, savedEntry)
    }

    companion object {
        private const val MAX_READER_BYTES = 512 * 1024

        val DEFAULT_ALLOWED_PUBLIC_DOMAINS: Set<String> = setOf(
            "github.com",
            "raw.githubusercontent.com",
            "www.kaggle.com",
            "kaggle.com",
            "developers.google.com",
            "developer.android.com",
        )

        internal fun extractReadableTitleAndText(raw: String, fallbackUrl: String): Pair<String, String> {
            val titleMatch = Regex("(?is)<title[^>]*>(.*?)</title>").find(raw)
            val rawTitle = titleMatch?.groupValues?.get(1)
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: fallbackUrl.substringAfterLast('/').ifBlank { fallbackUrl }

            val withoutScripts = raw
                .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
                .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
                .replace(Regex("(?is)<!--.*?-->"), " ")
                .replace(Regex("(?is)<(br|p|div|li|h[1-6]|tr)[^>]*>"), "\n")
                .replace(Regex("(?is)<[^>]+>"), " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")

            val cleanedLines = withoutScripts
                .lines()
                .map { it.replace(Regex("[ \\t]+"), " ").trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")

            return rawTitle.take(120) to cleanedLines.ifBlank { "(empty document)" }
        }
    }
}

sealed interface ControlledNavigationDecision {
    data class AllowInWorkspace(val normalizedUrl: String) : ControlledNavigationDecision
    data class UseCustomTabInstead(val url: String, val reason: String) : ControlledNavigationDecision
    data class Block(val reason: String) : ControlledNavigationDecision
}
