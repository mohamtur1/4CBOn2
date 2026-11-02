package io.github.mohamtur1.fourcbon2

import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubRepositoryReadClientTest {
    @Test
    fun publicProbeDoesNotReadAnyCredential() {
        val result = GitHubRepositoryReadClient(
            credentialStore = RejectingCredentialStore,
            connectionFactory = { url -> FakeHttpURLConnection(url, responseStatus = 200) },
        ).probePublicRepository()

        assertEquals(GitHubReadResult.RepositoryVisible, result)
    }

    @Test
    fun authorizedProbeStopsWhenNoLocalCredentialExists() {
        val result = GitHubRepositoryReadClient(
            credentialStore = EmptyCredentialStore,
            connectionFactory = { throw AssertionError("Connection must not start without a credential.") },
        ).probeAuthorizedRepository()

        assertEquals(GitHubReadResult.CredentialMissing, result)
    }

    private object RejectingCredentialStore : CredentialStore {
        override fun save(slot: CredentialSlot, secret: CharArray) = error("Unexpected save")
        override fun load(slot: CredentialSlot): CharArray? = error("Public probe must not load a credential")
        override fun remove(slot: CredentialSlot) = error("Unexpected remove")
        override fun contains(slot: CredentialSlot): Boolean = error("Unexpected contains")
    }

    private object EmptyCredentialStore : CredentialStore {
        override fun save(slot: CredentialSlot, secret: CharArray) = error("Unexpected save")
        override fun load(slot: CredentialSlot): CharArray? = null
        override fun remove(slot: CredentialSlot) = error("Unexpected remove")
        override fun contains(slot: CredentialSlot): Boolean = false
    }

    private class FakeHttpURLConnection(
        url: URL,
        private val responseStatus: Int,
    ) : HttpURLConnection(url) {
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
        override fun getResponseCode(): Int = responseStatus
    }
}
