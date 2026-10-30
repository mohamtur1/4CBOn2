package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLeaseTest {
    private var now = CREATED_AT

    @Test
    fun newLeaseIsActiveAtTheInitialEpochAndExpiresAtTheDerivedBoundary() {
        val lease = syntheticLease()

        assertEquals(LeaseState.ACTIVE, lease.state)
        assertEquals(LeaseBounds.INITIAL_EPOCH, lease.epoch)
        assertEquals(CREATED_AT + LeaseBounds.MAX_DURATION_MILLIS, lease.expiresAtEpochMillis)
        assertNull(lease.stepRefusal(now))
        assertTrue(lease.isReadyForStep(now))

        now = CREATED_AT + LeaseBounds.MAX_DURATION_MILLIS - 1
        assertNull(lease.stepRefusal(now))

        now = CREATED_AT + LeaseBounds.MAX_DURATION_MILLIS
        assertTrue(lease.isExpiredAt(now))
        assertEquals(LeaseRefusal.LEASE_EXPIRED, lease.stepRefusal(now))
    }

    @Test
    fun durationOutsideTheBoundedWindowCannotBeConstructed() {
        listOf(0L, -1L, LeaseBounds.MAX_DURATION_MILLIS + 1).forEach { duration ->
            assertThrows(IllegalArgumentException::class.java) {
                syntheticLease(durationMillis = duration)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            syntheticLease(createdAtEpochMillis = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LeaseCommand(LeaseCommandKind.STOP, LeaseCommandOrigin.OWNER_LOCAL, epoch = 0, issuedAtEpochMillis = 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LeaseCommand(LeaseCommandKind.STOP, LeaseCommandOrigin.OWNER_LOCAL, epoch = 1, issuedAtEpochMillis = 0L)
        }
        assertEquals(LeaseBounds.MAX_DURATION_MILLIS, LeaseBounds.MAX_DURATION_MILLIS)
        assertEquals(ScopeBounds.MAX_DURATION_MILLIS, LeaseBounds.MAX_DURATION_MILLIS)
    }

    @Test
    fun takeoverBumpsTheEpochAndBlocksStepsUntilOwnerResumeAndFreshCheck() {
        val lease = syntheticLease()

        val takenOver = lease.apply(ownerCommand(LeaseCommandKind.TAKE_OVER, epoch = 1))

        assertEquals(
            LeaseOutcome.Applied(
                kind = LeaseCommandKind.TAKE_OVER,
                previousState = LeaseState.ACTIVE,
                state = LeaseState.OWNER_TAKEOVER,
                epoch = 2,
            ),
            takenOver,
        )
        assertEquals(LeaseRefusal.OWNER_TAKEOVER_ACTIVE, lease.stepRefusal(now))

        // The automation must not be able to resume by itself.
        assertEquals(
            LeaseOutcome.Refused(
                kind = LeaseCommandKind.RESUME,
                reason = LeaseRefusal.NOT_OWNER_ORIGINATED,
                currentState = LeaseState.OWNER_TAKEOVER,
                currentEpoch = 2,
            ),
            lease.apply(
                LeaseCommand(
                    kind = LeaseCommandKind.RESUME,
                    origin = LeaseCommandOrigin.AUTOMATION,
                    epoch = 2,
                    issuedAtEpochMillis = now,
                ),
            ),
        )

        val resumed = lease.apply(ownerCommand(LeaseCommandKind.RESUME, epoch = 2))
        assertEquals(LeaseState.ACTIVE, lease.state)
        assertEquals(3, lease.epoch)
        assertTrue(resumed is LeaseOutcome.Applied)

        // Active again, but the fresh state check is still outstanding.
        assertEquals(LeaseRefusal.FRESH_STATE_REQUIRED, lease.stepRefusal(now))

        lease.confirmFreshState(confirmingEpoch = 3)
        assertNull(lease.stepRefusal(now))
    }

    @Test
    fun staleAndUnissuedEpochsAreRefusedWithExplicitReasons() {
        val lease = syntheticLease()
        lease.apply(ownerCommand(LeaseCommandKind.PAUSE, epoch = 1))
        assertEquals(2, lease.epoch)

        assertEquals(
            LeaseOutcome.Refused(
                kind = LeaseCommandKind.STOP,
                reason = LeaseRefusal.STALE_EPOCH,
                currentState = LeaseState.PAUSED,
                currentEpoch = 2,
            ),
            lease.apply(ownerCommand(LeaseCommandKind.STOP, epoch = 1)),
        )
        assertEquals(LeaseState.PAUSED, lease.state)

        assertEquals(
            LeaseRefusal.UNISSUED_EPOCH,
            (lease.apply(ownerCommand(LeaseCommandKind.STOP, epoch = 99)) as LeaseOutcome.Refused).reason,
        )
        assertEquals(2, lease.epoch)
    }

    @Test
    fun stopIsTerminalAndRefusesEveryLaterCommandAndStep() {
        val lease = syntheticLease()

        val stopped = lease.apply(ownerCommand(LeaseCommandKind.STOP, epoch = 1))
        assertEquals(
            LeaseOutcome.Applied(
                kind = LeaseCommandKind.STOP,
                previousState = LeaseState.ACTIVE,
                state = LeaseState.STOPPED,
                epoch = 2,
            ),
            stopped,
        )

        listOf(LeaseCommandKind.TAKE_OVER, LeaseCommandKind.PAUSE, LeaseCommandKind.RESUME, LeaseCommandKind.STOP)
            .forEach { kind ->
                val outcome = lease.apply(ownerCommand(kind, epoch = 2)) as LeaseOutcome.Refused
                assertEquals(LeaseRefusal.LEASE_TERMINATED, outcome.reason)
                assertEquals(LeaseState.STOPPED, lease.state)
            }

        assertEquals(LeaseRefusal.LEASE_TERMINATED, lease.stepRefusal(now))
        assertEquals(
            LeaseRefusal.LEASE_TERMINATED,
            (lease.confirmFreshState(2) as LeaseOutcome.Refused).reason,
        )
        assertEquals(2, lease.epoch)
    }

    @Test
    fun pauseMayComeFromAutomationButResumeMayNot() {
        val lease = syntheticLease()

        val paused = lease.apply(
            LeaseCommand(
                kind = LeaseCommandKind.PAUSE,
                origin = LeaseCommandOrigin.AUTOMATION,
                epoch = 1,
                issuedAtEpochMillis = now,
            ),
        )
        assertTrue("stopping is always allowed, even for the automation", paused is LeaseOutcome.Applied)
        assertEquals(LeaseState.PAUSED, lease.state)
        assertEquals(LeaseRefusal.PAUSED, lease.stepRefusal(now))

        assertEquals(
            LeaseRefusal.NOT_OWNER_ORIGINATED,
            (
                lease.apply(
                    LeaseCommand(
                        kind = LeaseCommandKind.RESUME,
                        origin = LeaseCommandOrigin.AUTOMATION,
                        epoch = 2,
                        issuedAtEpochMillis = now,
                    ),
                ) as LeaseOutcome.Refused
                ).reason,
        )
        assertEquals(LeaseState.PAUSED, lease.state)

        assertTrue(lease.apply(ownerCommand(LeaseCommandKind.RESUME, epoch = 2)) is LeaseOutcome.Applied)
    }

    @Test
    fun redundantAndExpiredTransitionsAreRefusedHonestly() {
        val active = syntheticLease()
        assertEquals(
            LeaseRefusal.INVALID_TRANSITION,
            (active.apply(ownerCommand(LeaseCommandKind.RESUME, epoch = 1)) as LeaseOutcome.Refused).reason,
        )

        val paused = syntheticLease()
        paused.apply(ownerCommand(LeaseCommandKind.PAUSE, epoch = 1))
        assertEquals(
            LeaseRefusal.INVALID_TRANSITION,
            (paused.apply(ownerCommand(LeaseCommandKind.PAUSE, epoch = 2)) as LeaseOutcome.Refused).reason,
        )

        val expiring = syntheticLease()
        now = CREATED_AT + LeaseBounds.MAX_DURATION_MILLIS
        assertEquals(
            LeaseRefusal.LEASE_EXPIRED,
            (expiring.apply(ownerCommand(LeaseCommandKind.RESUME, epoch = 1)) as LeaseOutcome.Refused).reason,
        )
        // A restrictive command still applies after expiry, so safety never depends on a clock.
        assertTrue(expiring.apply(ownerCommand(LeaseCommandKind.STOP, epoch = 1)) is LeaseOutcome.Applied)
        assertEquals(LeaseState.STOPPED, expiring.state)
    }

    @Test
    fun freshStateCheckMustMatchTheCurrentEpochAndActiveState() {
        val lease = syntheticLease()
        lease.apply(ownerCommand(LeaseCommandKind.PAUSE, epoch = 1))
        assertEquals(
            LeaseRefusal.STALE_EPOCH,
            (lease.confirmFreshState(1) as LeaseOutcome.Refused).reason,
        )
        assertEquals(
            LeaseRefusal.INVALID_TRANSITION,
            (lease.confirmFreshState(2) as LeaseOutcome.Refused).reason,
        )

        lease.apply(ownerCommand(LeaseCommandKind.RESUME, epoch = 2))
        assertEquals(LeaseRefusal.FRESH_STATE_REQUIRED, lease.snapshot(now).refusal)
        assertFalse(lease.snapshot(now).isReadyForStep)

        assertEquals(
            LeaseRefusal.STALE_EPOCH,
            (lease.confirmFreshState(2) as LeaseOutcome.Refused).reason,
        )
        lease.confirmFreshState(3)

        val snapshot = lease.snapshot(now)
        assertEquals(LeaseState.ACTIVE, snapshot.state)
        assertEquals(3, snapshot.epoch)
        assertNull(snapshot.refusal)
        assertTrue(snapshot.isReadyForStep)
    }

    private fun syntheticLease(
        createdAtEpochMillis: Long = CREATED_AT,
        durationMillis: Long = LeaseBounds.MAX_DURATION_MILLIS,
    ) = ActionLease(
        createdAtEpochMillis = createdAtEpochMillis,
        durationMillis = durationMillis,
        nowEpochMillis = { now },
    )

    private fun ownerCommand(kind: LeaseCommandKind, epoch: Int) = LeaseCommand(
        kind = kind,
        origin = LeaseCommandOrigin.OWNER_LOCAL,
        epoch = epoch,
        issuedAtEpochMillis = now,
    )

    private companion object {
        const val CREATED_AT = 1_700_000_000_000L
    }
}
