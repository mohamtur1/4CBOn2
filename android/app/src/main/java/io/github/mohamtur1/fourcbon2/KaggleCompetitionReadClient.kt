package io.github.mohamtur1.fourcbon2

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Read-only competition-list metadata probe. It never joins competitions, downloads data,
 * starts a notebook, creates a submission, or stores response bodies.
 */
class KaggleCompetitionReadClient(
    private val credentialStore: CredentialStore,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) {
    /** Checks whether Kaggle permits this public endpoint without a credential. */
    fun probePublicCompetitionList(): KaggleReadResult = probe(accessToken = null)

    /**
     * Requires a future, locally stored Kaggle bearer access token from a verified flow.
     * A generic account password, username/key pair, browser cookie, or copied session value is not accepted here.
     */
    fun probeAuthorizedCompetitionList(): KaggleReadResult {
        val secret = credentialStore.load(CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN)
            ?: return KaggleReadResult.CredentialMissing
        val accessToken = secret.concatToString()
        return try {
            probe(accessToken)
        } finally {
            secret.fill('\u0000')
        }
    }

    private fun probe(accessToken: String?): KaggleReadResult = try {
        val connection = connectionFactory(KaggleCompetitionEndpoint.listUrl())
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (accessToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }

            KaggleReadResult.fromHttpResponse(connection.responseCode)
        } finally {
            connection.disconnect()
        }
    } catch (_: IOException) {
        KaggleReadResult.NetworkUnavailable
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 10_000
    }
}
