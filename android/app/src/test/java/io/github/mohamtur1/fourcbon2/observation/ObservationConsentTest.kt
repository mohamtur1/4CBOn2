package io.github.mohamtur1.fourcbon2.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationConsentTest {

    @Test
    fun captureStartsOffByDefault() {
        val consent = ObservationConsent()
        assertEquals(ObservationConsent.CaptureConsent.OFF, consent.currentCaptureConsent())
        assertFalse(consent.mayObserve(ObservationConsent.SensitiveContext.NONE, captureConsentOn = true))
    }

    @Test
    fun captureRequiresExplicitConsent() {
        val consent = ObservationConsent()
        consent.enableCapture()
        assertEquals(ObservationConsent.CaptureConsent.ON, consent.currentCaptureConsent())
        assertTrue(consent.mayObserve(ObservationConsent.SensitiveContext.NONE, captureConsentOn = true))
    }

    @Test
    fun sensitiveContextsAreAlwaysExcluded() {
        val consent = ObservationConsent()
        consent.enableCapture()
        assertFalse(consent.mayObserve(ObservationConsent.SensitiveContext.BANKING_OR_PAYMENT))
        assertFalse(consent.mayObserve(ObservationConsent.SensitiveContext.PASSWORD_OR_OTP))
        assertFalse(consent.mayObserve(ObservationConsent.SensitiveContext.BIOMETRIC))
        assertFalse(consent.mayObserve(ObservationConsent.SensitiveContext.PERMISSION_GRANT))
        assertFalse(consent.mayObserve(ObservationConsent.SensitiveContext.SECURITY_SETTINGS))
    }

    @Test
    fun cloudUploadStartsOffAndRequiresExplicitEnable() {
        val consent = ObservationConsent()
        assertFalse(consent.isCloudUploadEnabled())
        assertFalse(consent.mayUploadObservedData())
        consent.enableCloudUpload()
        assertTrue(consent.mayUploadObservedData())
        consent.disableCloudUpload()
        assertFalse(consent.mayUploadObservedData())
    }
}
