package io.github.mohamtur1.fourcbon2.lab

/**
 * Conditional personal-lab UI runner eligibility (Division 34).
 *
 * The runner is available only for a permitted, authorized experimental
 * configuration against an owner-controlled fixture app. It is unavailable
 * when eligibility fails, and it is **not** Play-compliance proof.
 */
class LabRunnerEligibility {
    data class Eligibility(
        val eligible: Boolean,
        val reason: String,
    )

    fun check(
        experimentalConfigAllowed: Boolean,
        ownerControlledFixtureAvailable: Boolean,
        deviceTested: Boolean,
    ): Eligibility {
        return when {
            !experimentalConfigAllowed -> Eligibility(false, "Experimental configuration is not enabled.")
            !ownerControlledFixtureAvailable -> Eligibility(false, "Owner-controlled fixture app is not available.")
            !deviceTested -> Eligibility(false, "Fixture has not been tested on a device.")
            else -> Eligibility(true, "Eligible for personal-lab use only.")
        }
    }

    /** The runner never claims Play Store compliance or store eligibility. */
    fun claimsPlayCompliance(): Boolean = false
}
