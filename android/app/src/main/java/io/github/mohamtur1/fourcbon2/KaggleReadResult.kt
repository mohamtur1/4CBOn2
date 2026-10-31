package io.github.mohamtur1.fourcbon2

/** A concise, non-secret result of a competition-list metadata probe. */
sealed interface KaggleReadResult {
    data object CredentialMissing : KaggleReadResult
    data object CompetitionListVisible : KaggleReadResult
    data object AuthenticationRequired : KaggleReadResult
    data object AccessForbidden : KaggleReadResult
    data object RateLimited : KaggleReadResult
    data object ServiceUnavailable : KaggleReadResult
    data object NetworkUnavailable : KaggleReadResult
    data class UnexpectedResponse(val statusCode: Int) : KaggleReadResult

    companion object {
        fun fromHttpResponse(statusCode: Int): KaggleReadResult = when (statusCode) {
            200 -> CompetitionListVisible
            401 -> AuthenticationRequired
            403 -> AccessForbidden
            429 -> RateLimited
            in 500..599 -> ServiceUnavailable
            else -> UnexpectedResponse(statusCode)
        }
    }
}
