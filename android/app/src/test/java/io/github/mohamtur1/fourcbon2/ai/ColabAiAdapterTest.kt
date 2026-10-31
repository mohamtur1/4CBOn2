package io.github.mohamtur1.fourcbon2.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColabAiAdapterTest {

    @Test
    fun unauthorizedAccountIsNotConfigured() {
        val probe = ColabAiAdapter().probe(accountAuthorized = false, runtimeAvailable = true)
        assertEquals(ColabAiAdapter.Capability.NOT_CONFIGURED, probe.capability)
    }

    @Test
    fun missingRuntimeIsReportedHonestly() {
        val probe = ColabAiAdapter().probe(accountAuthorized = true, runtimeAvailable = false)
        assertEquals(ColabAiAdapter.Capability.RUNTIME_UNAVAILABLE, probe.capability)
        assertTrue(probe.detail.contains("runtime"))
    }

    @Test
    fun verifiedProbeEnablesBoundedRequest() {
        val adapter = ColabAiAdapter()
        val probe = adapter.probe(accountAuthorized = true, runtimeAvailable = true, aiServiceAvailable = true)
        assertEquals(ColabAiAdapter.Capability.CAPABILITY_VERIFIED, probe.capability)
        val request = adapter.boundedRequest("summarize", 128, probe)
        assertTrue(request.accepted)
    }

    @Test
    fun boundedRequestIsRejectedWithoutVerifiedProbe() {
        val adapter = ColabAiAdapter()
        val probe = adapter.probe(accountAuthorized = true, runtimeAvailable = false)
        val request = adapter.boundedRequest("summarize", 128, probe)
        assertFalse(request.accepted)
    }

    @Test
    fun invalidBoundedRequestIsRejected() {
        val adapter = ColabAiAdapter()
        val probe = adapter.probe(accountAuthorized = true, runtimeAvailable = true, aiServiceAvailable = true)
        try {
            adapter.boundedRequest("", 128, probe)
            throw AssertionError("Expected blank prompt rejection")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
