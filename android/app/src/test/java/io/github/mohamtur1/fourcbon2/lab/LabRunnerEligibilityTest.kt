package io.github.mohamtur1.fourcbon2.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabRunnerEligibilityTest {

    @Test
    fun eligibleOnlyWithConfigFixtureAndDeviceTest() {
        val eligibility = LabRunnerEligibility()
        val ok = eligibility.check(
            experimentalConfigAllowed = true,
            ownerControlledFixtureAvailable = true,
            deviceTested = true,
        )
        assertTrue(ok.eligible)
        assertTrue(ok.reason.contains("personal-lab"))
    }

    @Test
    fun eachMissingConditionBlocks() {
        val eligibility = LabRunnerEligibility()
        assertFalse(
            eligibility.check(false, true, true).eligible,
        )
        assertFalse(
            eligibility.check(true, false, true).eligible,
        )
        assertFalse(
            eligibility.check(true, true, false).eligible,
        )
    }

    @Test
    fun neverClaimsPlayCompliance() {
        assertFalse(LabRunnerEligibility().claimsPlayCompliance())
    }
}
