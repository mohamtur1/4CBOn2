package io.github.mohamtur1.fourcbon2.local

import kotlin.io.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDocumentStoreTest {

    @Test
    fun dataSurvivesRestart() {
        val dir = createTempDirectory("store-restart")
        val first = LocalDocumentStore(dir)
        first.writeCollection("jobs") { "alpha" }
        first.writeCollection("drafts") { "beta" }

        val second = LocalDocumentStore(dir)
        assertEquals(listOf("alpha"), second.readCollection("jobs") { it })
        assertEquals(listOf("beta"), second.readCollection("drafts") { it })
    }

    @Test
    fun emptyWhenMissing() {
        val store = LocalDocumentStore(createTempDirectory("store-missing"))
        assertEquals(0, store.readCollection("nothing") { it }.size)
    }

    @Test
    fun roundTripsArbitraryText() {
        val dir = createTempDirectory("store-special")
        val store = LocalDocumentStore(dir)
        val tricky = "line1\nline2\twith \\ backslash \u001f and unit separator"
        store.writeCollection("notes") { tricky }
        assertEquals(listOf(tricky), store.readCollection("notes") { it })
    }
}
