package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {
    @Test
    fun navigationIncludesTheFivePlannedFoundationDestinations() {
        assertEquals(
            listOf("Home", "Ask", "Agents", "Browser", "Activity"),
            AppDestination.entries.map { it.label },
        )
    }
}
