package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColabNotebookReferenceTest {
    @Test
    fun handoffKeepsTheExactApprovedNotebookIdentity() {
        val handoff = ColabNotebookReference.manualHandoff()

        assertEquals("4CBOn2_Kaggle6.ipynb", handoff.notebookFileName)
        assertEquals(
            "https://colab.research.google.com/github/mohamtur1/4CBOn2/blob/main/4CBOn2_Kaggle6.ipynb",
            handoff.destinationUrl,
        )
    }

    @Test
    fun entryRouteIsExplicitlyManualOnly() {
        assertEquals(
            ColabControlCapability.MANUAL_BROWSER_HANDOFF_ONLY,
            ColabNotebookReference.manualHandoff().capability,
        )
        assertTrue(
            ColabControlCapability.entries.contains(
                ColabControlCapability.PROGRAMMATIC_RUNTIME_CONTROL_UNAVAILABLE,
            ),
        )
        assertTrue(
            ColabControlCapability.entries.contains(ColabControlCapability.BUILT_IN_AI_UNPROBED),
        )
    }
}
