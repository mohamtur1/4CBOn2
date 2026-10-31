package io.github.mohamtur1.fourcbon2.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomTabOpenerTest {

    private val policy = BrowserWorkspacePolicy(
        allowedHosts = listOf("colab.research.google.com", "www.kaggle.com"),
    )

    @Test
    fun httpsPermittedHostOpensInCustomTab() {
        val decision = CustomTabOpener().decide("https://colab.research.google.com/notebook", true, policy)
        assertEquals(CustomTabOpener.Outcome.OPEN_IN_CUSTOM_TAB, decision.outcome)
    }

    @Test
    fun missingChromeFallsBackToExternalBrowser() {
        val decision = CustomTabOpener().decide("https://www.kaggle.com/competitions", false, policy)
        assertEquals(CustomTabOpener.Outcome.FALLBACK_TO_EXTERNAL_BROWSER, decision.outcome)
    }

    @Test
    fun nonHttpsIsRejected() {
        val decision = CustomTabOpener().decide("http://www.kaggle.com/x", true, policy)
        assertEquals(CustomTabOpener.Outcome.REJECTED, decision.outcome)
    }

    @Test
    fun unpermittedHostIsRejected() {
        val decision = CustomTabOpener().decide("https://evil.example.com/x", true, policy)
        assertEquals(CustomTabOpener.Outcome.REJECTED, decision.outcome)
    }

    @Test
    fun invalidUrlIsRejected() {
        val decision = CustomTabOpener().decide("not a url", true, policy)
        assertEquals(CustomTabOpener.Outcome.REJECTED, decision.outcome)
    }
}
