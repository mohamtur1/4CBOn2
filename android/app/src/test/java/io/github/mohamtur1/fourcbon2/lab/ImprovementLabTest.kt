package io.github.mohamtur1.fourcbon2.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImprovementLabTest {

    @Test
    fun candidatePromotedOnlyWithBetterMetricAndApproval() {
        val lab = ImprovementLab()
        lab.recordVersion("baseline", "v1")
        lab.recordVersion("candidate", "v2")

        val notBetter = lab.evaluate("baseline", "candidate", baselineMetric = 0.8, candidateMetric = 0.7, promotionAllowed = true)
        assertFalse(notBetter.promoted)
        assertTrue(notBetter.reason.contains("does not beat"))

        val betterNoApproval = lab.evaluate("baseline", "candidate", baselineMetric = 0.7, candidateMetric = 0.8, promotionAllowed = false)
        assertFalse(betterNoApproval.promoted)
        assertTrue(betterNoApproval.reason.contains("promotion was not approved"))

        val promoted = lab.evaluate("baseline", "candidate", baselineMetric = 0.7, candidateMetric = 0.8, promotionAllowed = true)
        assertTrue(promoted.promoted)
        assertEquals(3, lab.allReports().size)
    }

    @Test
    fun versionsArePreserved() {
        val lab = ImprovementLab()
        lab.recordVersion("baseline", "content-1")
        assertEquals("content-1", lab.versionContent("baseline"))
        assertNull(lab.versionContent("missing"))
    }

    @Test
    fun evaluateRequiresPreservedVersions() {
        val lab = ImprovementLab()
        try {
            lab.evaluate("baseline", "candidate", 0.5, 0.9, promotionAllowed = true)
            throw AssertionError("Expected missing version rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun reportKeepsBothMetrics() {
        val lab = ImprovementLab()
        lab.recordVersion("baseline", "v1")
        lab.recordVersion("candidate", "v2")
        val report = lab.evaluate("baseline", "candidate", baselineMetric = 0.5, candidateMetric = 0.9, promotionAllowed = true)
        assertEquals(0.5, report.baselineMetric, 0.0)
        assertEquals(0.9, report.candidateMetric, 0.0)
        assertNotNull(report.reason)
    }
}
