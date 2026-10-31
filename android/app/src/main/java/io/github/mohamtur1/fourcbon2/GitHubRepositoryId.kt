package io.github.mohamtur1.fourcbon2

import java.net.URL

/** Restricts the first GitHub adapter to a validated owner/repository pair. */
data class GitHubRepositoryId(
    val owner: String,
    val repository: String,
) {
    init {
        require(COMPONENT_PATTERN.matches(owner)) { "Invalid GitHub owner." }
        require(COMPONENT_PATTERN.matches(repository)) { "Invalid GitHub repository." }
    }

    fun metadataUrl(): URL = URL("https://api.github.com/repos/$owner/$repository")

    companion object {
        private val COMPONENT_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
        val FOUR_CB_ON_2 = GitHubRepositoryId(
            owner = "mohamtur1",
            repository = "4CBOn2",
        )
    }
}
