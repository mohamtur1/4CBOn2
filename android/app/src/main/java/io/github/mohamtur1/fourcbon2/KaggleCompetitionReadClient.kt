package io.github.mohamtur1.fourcbon2

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Read-only Kaggle competition-list metadata boundary. It never joins a competition, downloads data,
 * starts a notebook, creates a submission, or logs credentials.
 *
 * Supports two explicitly distinct local credential slots:
 * - [CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN] -> `Authorization: Bearer <token>`
 * - [CredentialSlot.KAGGLE_API_TOKEN] -> `Authorization: Basic <base64(username:key)>`
 */
class KaggleCompetitionReadClient(
    private val credentialStore: CredentialStore,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) {
    /** Checks whether Kaggle permits this public endpoint without a credential. */
    fun probePublicCompetitionList(): KaggleReadResult = probe(authorizationHeader = null)

    /**
     * Probes the read-only competition list using a locally stored Kaggle credential.
     * Checks [CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN] first, then [CredentialSlot.KAGGLE_API_TOKEN].
     * Returns [KaggleReadResult.CredentialMissing] without opening a connection if neither exists.
     */
    fun probeAuthorizedCompetitionList(): KaggleReadResult {
        val bearerSecret = credentialStore.load(CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN)
        if (bearerSecret != null) {
            return try {
                probe(authorizationHeader = "Bearer ${bearerSecret.concatToString()}")
            } finally {
                bearerSecret.fill('\u0000')
            }
        }

        val apiPairSecret = credentialStore.load(CredentialSlot.KAGGLE_API_TOKEN)
            ?: return KaggleReadResult.CredentialMissing
        val rawBytes = apiPairSecret.concatToString().toByteArray(Charsets.UTF_8)
        return try {
            val encoded = Base64.getEncoder().encodeToString(rawBytes)
            probe(authorizationHeader = "Basic $encoded")
        } finally {
            apiPairSecret.fill('\u0000')
            rawBytes.fill(0)
        }
    }

    private fun probe(authorizationHeader: String?): KaggleReadResult = try {
        val connection = connectionFactory(KaggleCompetitionEndpoint.listUrl())
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (authorizationHeader != null) {
                connection.setRequestProperty("Authorization", authorizationHeader)
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
