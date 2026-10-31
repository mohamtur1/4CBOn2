package io.github.mohamtur1.fourcbon2

/** A concise, non-secret result of a repository metadata probe. */
sealed interface GitHubReadResult {
    data object CredentialMissing : GitHubReadResult
    data object RepositoryVisible : GitHubReadResult
    data object AuthorizationRejected : GitHubReadResult
    data object AccessForbidden : GitHubReadResult
    data object RateLimited : GitHubReadResult
    data object NotFoundOrPrivate : GitHubReadResult
    data object ServiceUnavailable : GitHubReadResult
    data object NetworkUnavailable : GitHubReadResult
    data class UnexpectedResponse(val statusCode: Int) : GitHubReadResult

    companion object {
        fun fromHttpResponse(
            statusCode: Int,
            rateLimitRemaining: String?,
        ): GitHubReadResult = when (statusCode) {
            200 -> RepositoryVisible
            401 -> AuthorizationRejected
            403 -> if (rateLimitRemaining == "0") RateLimited else AccessForbidden
            404 -> NotFoundOrPrivate
            429 -> RateLimited
            in 500..599 -> ServiceUnavailable
            else -> UnexpectedResponse(statusCode)
        }
    }
}
