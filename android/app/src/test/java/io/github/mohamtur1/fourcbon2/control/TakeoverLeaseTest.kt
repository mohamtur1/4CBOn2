package io.github.mohamtur1.fourcbon2.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeoverLeaseTest {

    @Test
    fun takeOverStopsControlAndRejectsStaleCommands() {
        val lease = TakeoverLease()
        assertTrue(lease.beginControl("owner"))
        assertTrue(lease.isControlActive())

        assertTrue(lease.takeOver("owner"))
        assertFalse(lease.isControlActive())
        assertFalse(lease.acceptCommand(commandEpoch = lease.currentEpoch(), commandOwner = "owner"))

        // A command with an old epoch is rejected even before takeover increments.
        val lease2 = TakeoverLease()
        lease2.beginControl("owner")
        assertTrue(lease2.acceptCommand(commandEpoch = 0, commandOwner = "owner"))
        lease2.takeOver("owner")
        assertFalse(lease2.acceptCommand(commandEpoch = 0, commandOwner = "owner"))
    }

    @Test
    fun resumeRequiresExplicitOwnerAction() {
        val lease = TakeoverLease()
        lease.beginControl("owner")
        lease.takeOver("owner")
        assertTrue(lease.resume("owner"))
        assertTrue(lease.isControlActive())
    }

    @Test
    fun cancelInvalidatesSessionPermanently() {
        val lease = TakeoverLease()
        lease.beginControl("owner")
        lease.takeOver("owner")
        assertTrue(lease.cancel("owner"))
        assertFalse(lease.resume("owner"))
        assertFalse(lease.acceptCommand(commandEpoch = lease.currentEpoch(), commandOwner = "owner"))
    }

    @Test
    fun wrongOwnerIsRejected() {
        val lease = TakeoverLease()
        lease.beginControl("owner")
        assertFalse(lease.takeOver("intruder"))
        assertFalse(lease.cancel("intruder"))
        assertFalse(lease.acceptCommand(commandEpoch = 0, commandOwner = "intruder"))
        assertFalse(lease.resume("intruder"))
        assertEquals(TakeoverLease.State.IDLE, lease.currentState())
    }
}
