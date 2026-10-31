package io.github.mohamtur1.fourcbon2.observation

/**
 * Consented observation boundary (Division 26).
 *
 * Observation is separate from action authority. Shared/structured data is
 * preferred; screen capture starts **Off by default** and requires explicit
 * consent. Sensitive contexts are always excluded. Cloud upload is Off by
 * default.
 */
class ObservationConsent {
    enum class CaptureConsent { OFF, ON }

    enum class SensitiveContext {
        NONE,
        BANKING_OR_PAYMENT,
        PASSWORD_OR_OTP,
        BIOMETRIC,
        PERMISSION_GRANT,
        SECURITY_SETTINGS,
    }

    private var captureConsent: CaptureConsent = CaptureConsent.OFF
    private var cloudUploadEnabled: Boolean = false

    fun currentCaptureConsent(): CaptureConsent = captureConsent

    fun enableCapture(): Boolean {
        captureConsent = CaptureConsent.ON
        return true
    }

    fun disableCapture(): Boolean {
        captureConsent = CaptureConsent.OFF
        return true
    }

    fun isCloudUploadEnabled(): Boolean = cloudUploadEnabled

    fun enableCloudUpload(): Boolean {
        cloudUploadEnabled = true
        return true
    }

    fun disableCloudUpload(): Boolean {
        cloudUploadEnabled = false
        return true
    }

    /**
     * Whether the given context may be observed. Sensitive contexts are
     * excluded regardless of consent; observation without capture consent is
     * limited to structured/shared data only.
     */
    fun mayObserve(context: SensitiveContext, captureConsentOn: Boolean = true): Boolean {
        if (context != SensitiveContext.NONE) return false
        return captureConsent == CaptureConsent.ON && captureConsentOn
    }

    /** Whether a cloud upload of observed data is allowed right now. */
    fun mayUploadObservedData(context: SensitiveContext = SensitiveContext.NONE): Boolean =
        cloudUploadEnabled && context == SensitiveContext.NONE
}
