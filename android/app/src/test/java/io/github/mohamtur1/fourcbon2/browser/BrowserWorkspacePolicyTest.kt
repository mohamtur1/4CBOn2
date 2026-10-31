package io.github.mohamtur1.fourcbon2.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserWorkspacePolicyTest {

    private val policy = BrowserWorkspacePolicy(
        allowedHosts = listOf("colab.research.google.com", "www.kaggle.com", "github.com"),
    )

    @Test
    fun allowedHostIsPermitted() {
        assertTrue(policy.isPermitted("www.kaggle.com"))
        assertTrue(policy.allowsNavigation("https://www.kaggle.com/competitions"))
    }

    @Test
    fun unknownHostIsDenied() {
        assertFalse(policy.isPermitted("unknown.example.com"))
        assertFalse(policy.allowsNavigation("https://unknown.example.com/x"))
    }

    @Test
    fun privateAndMetadataDestinationsAreAlwaysDenied() {
        assertFalse(policy.isPermitted("metadata.google.internal"))
        assertFalse(policy.isPermitted("169.254.169.254"))
        assertFalse(policy.isPermitted("localhost"))
        assertFalse(policy.isPermitted("127.0.0.1"))
    }

    @Test
    fun nonHttpsNavigationIsDenied() {
        assertFalse(policy.allowsNavigation("http://www.kaggle.com/x"))
        assertFalse(policy.allowsNavigation("file:///etc/hosts"))
    }

    @Test
    fun noPrivilegedNativeBridgeIsExposed() {
        assertFalse(policy.allowsNativeBridge())
    }
}
