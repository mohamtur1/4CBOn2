package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialSlotTest {
    @Test
    fun onlyPlannedUserCredentialSlotsExist() {
        assertEquals(
            listOf(CredentialSlot.GITHUB_FINE_GRAINED_PAT),
            CredentialSlot.entries.toList(),
        )
    }
}
