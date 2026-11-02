package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubRepositoryIdTest {
    @Test
    fun intendedRepositoryUsesOnlyTheOfficialGitHubApiMetadataEndpoint() {
        assertEquals(
            "https://api.github.com/repos/mohamtur1/4CBOn2",
            GitHubRepositoryId.FOUR_CB_ON_2.metadataUrl().toString(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRepositoryComponentIsRejected() {
        GitHubRepositoryId(owner = "mohamtur1", repository = "../other")
    }
}
