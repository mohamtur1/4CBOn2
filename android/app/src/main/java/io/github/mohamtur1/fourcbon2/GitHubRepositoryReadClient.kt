package io.github.mohamtur1.fourcbon2

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Read-only metadata probe for the intended repository. It never writes, creates,
 * deletes, or executes repository content. Callers must only invoke it from an
 * explicit owner-started connection action when that UI exists.
 */
class GitHubRepositoryReadClient(
    private val credentialStore: CredentialStore,
    private val repository: GitHubRepositoryId = GitHubRepositoryId.FOUR_CB_ON_2,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) {
    /** Use only for public repository visibility/metadata; no secret is read. */
    fun probePublicRepository(): GitHubReadResult = probe(token = null)

    /**
     * Use only after a user has deliberately stored a fine-grained read credential locally.
     * This route does not fall back to anonymous access when the credential is absent.
     */
    fun probeAuthorizedRepository(): GitHubReadResult {
        val secret = credentialStore.load(CredentialSlot.GITHUB_FINE_GRAINED_PAT)
            ?: return GitHubReadResult.CredentialMissing
        val token = secret.concatToString()
        return try {
            probe(token)
        } finally {
            secret.fill('\u0000')
        }
    }

    private fun probe(token: String?): GitHubReadResult = try {
        val connection = connectionFactory(repository.metadataUrl())
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            val statusCode = connection.responseCode
            GitHubReadResult.fromHttpResponse(
                statusCode = statusCode,
                rateLimitRemaining = connection.getHeaderField("X-RateLimit-Remaining"),
            )
        } finally {
            connection.disconnect()
        }
    } catch (_: IOException) {
        GitHubReadResult.NetworkUnavailable
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 10_000
    }
}
