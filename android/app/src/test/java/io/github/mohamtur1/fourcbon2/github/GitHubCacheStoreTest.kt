package io.github.mohamtur1.fourcbon2.github

import io.github.mohamtur1.fourcbon2.local.LocalDocumentStore
import kotlin.io.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubCacheStoreTest {

    @Test
    fun refreshPermittedPathStoresVersion() {
        val store = GitHubCacheStore(LocalDocumentStore(createTempDirectory("gh-cache")))
        store.load()
        val entry = store.refresh(
            path = "notebook.ipynb",
            revision = "rev-9",
            fetcher = { "content-of-$it" },
            permittedPaths = listOf("notebook.ipynb"),
        )
        assertNotNull(entry)
        assertEquals("content-of-notebook.ipynb", entry?.content)
        assertEquals("rev-9", store.cached("notebook.ipynb")?.revision)
    }

    @Test
    fun nonPermittedPathIsRejected() {
        val store = GitHubCacheStore(LocalDocumentStore(createTempDirectory("gh-cache-denied")))
        store.load()
        try {
            store.refresh(
                path = "secrets.env",
                revision = "rev-1",
                fetcher = { "never" },
                permittedPaths = listOf("notebook.ipynb"),
            )
            throw AssertionError("Expected non-permitted rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun cacheSurvivesRestart() {
        val dir = createTempDirectory("gh-cache-restart")
        val first = GitHubCacheStore(LocalDocumentStore(dir))
        first.load()
        first.refresh("a.txt", "rev-1", { "hello" }, listOf("a.txt"))

        val second = GitHubCacheStore(LocalDocumentStore(dir))
        second.load()
        assertEquals("hello", second.cached("a.txt")?.content)
    }

    @Test
    fun missingPathReturnsNull() {
        val store = GitHubCacheStore(LocalDocumentStore(createTempDirectory("gh-cache-missing")))
        store.load()
        assertNull(store.cached("nothing.txt"))
    }
}
