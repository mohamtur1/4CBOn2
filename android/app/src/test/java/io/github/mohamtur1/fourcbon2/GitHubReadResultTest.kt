package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubReadResultTest {
    @Test
    fun knownHttpStatesMapToHonestReadOutcomes() {
        assertEquals(GitHubReadResult.RepositoryVisible, GitHubReadResult.fromHttpResponse(200, null))
        assertEquals(GitHubReadResult.AuthorizationRejected, GitHubReadResult.fromHttpResponse(401, null))
        assertEquals(GitHubReadResult.RateLimited, GitHubReadResult.fromHttpResponse(403, "0"))
        assertEquals(GitHubReadResult.AccessForbidden, GitHubReadResult.fromHttpResponse(403, "12"))
        assertEquals(GitHubReadResult.NotFoundOrPrivate, GitHubReadResult.fromHttpResponse(404, null))
    }
}
