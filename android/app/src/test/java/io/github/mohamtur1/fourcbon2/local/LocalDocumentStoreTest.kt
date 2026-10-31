package io.github.mohamtur1.fourcbon2.local

import io.github.mohamtur1.fourcbon2.testutil.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDocumentStoreTest {

    @Test
    fun dataSurvivesRestart() {
        val dir = createTempDirectory("store-restart")
        val first = LocalDocumentStore(dir)
        first.writeCollection("jobs", listOf("alpha")) { it }
        first.writeCollection("drafts", listOf("beta")) { it }

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
        store.writeCollection("notes", listOf(tricky)) { RecordCodec.escape(it) }
        assertEquals(listOf(tricky), store.readCollection("notes") { RecordCodec.unescape(it) })
    }
}
