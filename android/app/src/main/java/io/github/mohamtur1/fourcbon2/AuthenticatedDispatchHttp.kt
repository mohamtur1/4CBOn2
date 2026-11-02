package io.github.mohamtur1.fourcbon2

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Division 49 (live binding) — the only place a dispatch request touches the network.
 *
 * Security rules:
 *  - The credential is read from the Keystore-backed [CredentialStore] at call time, used for one
 *    header, and the char buffer is zeroed immediately afterwards.
 *  - The request body (which contains the candidate patch) is never logged, and neither is the
 *    credential. Errors are reported by exception *type*, not by echoing response bodies.
 *  - Timeouts are bounded so a stalled runner cannot hang the Daily Lab.
 */
class BearerAuthenticatedDispatchHttp(
    private val credentialProvider: () -> CharArray?,
    private val authorizationScheme: String = "Bearer",
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val connectionFactory: (String) -> HttpURLConnection = { endpoint ->
        URL(endpoint).openConnection() as HttpURLConnection
    },
) : DispatchHttpCall {
    override fun post(url: String, bodyJson: String, idempotencyKey: String): DispatchHttpResult {
        val secret = credentialProvider() ?: return DispatchHttpResult.NetworkError("missing credential")
        val connection = try {
            connectionFactory(url)
        } catch (io: IOException) {
            zero(secret)
            return DispatchHttpResult.NetworkError("unreachable endpoint")
        }
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Idempotency-Key", idempotencyKey)
            connection.setRequestProperty("Authorization", "$authorizationScheme ${String(secret)}")
            connection.setRequestProperty("User-Agent", "4CBOn2/0.3 (+https://github.com/mohamtur1/4CBOn2)")
            connection.outputStream.use { out ->
                out.write(bodyJson.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty().take(4_000)
            DispatchHttpResult.Response(statusCode = status, body = body)
        } catch (io: IOException) {
            // Ambiguous by construction: the request may have reached the runner.
            DispatchHttpResult.NetworkError("transport failure: ${io::class.java.simpleName}")
        } finally {
            runCatching { connection.disconnect() }
            zero(secret)
        }
    }

    private fun zero(buffer: CharArray) {
        buffer.fill('\u0000')
    }
}

/** Endpoint catalogue with explicit configuration state, so the UI can say "not configured". */
object DispatchEndpoints {
    const val SETTING_GITHUB_WORKFLOW_ENDPOINT = "dispatch_github_workflow_endpoint"
    const val SETTING_KAGGLE_KERNEL_ENDPOINT = "dispatch_kaggle_kernel_endpoint"

    /** KB-style GitHub workflow dispatch endpoint for the companion repository. */
    const val DEFAULT_GITHUB_WORKFLOW_ENDPOINT =
        "https://api.github.com/repos/mohamtur1/4CBOn2/actions/workflows/evaluate-candidate.yml/dispatches"

    /**
     * Kaggle's kernel-push endpoint needs a multipart notebook body, which this client does not
     * build. It stays empty (and therefore honestly "not configured") until the owner supplies an
     * endpoint that matches their own runner.
     */
    const val DEFAULT_KAGGLE_KERNEL_ENDPOINT = ""
}
