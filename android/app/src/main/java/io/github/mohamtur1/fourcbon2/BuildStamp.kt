package io.github.mohamtur1.fourcbon2

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The installed build's identity, readable on the phone and comparable with the GitHub release.
 *
 * Why this exists: the app showed no version, build date, or signature anywhere, so an installed
 * 4CBOn2 could not tell its owner which build it was. Two builds of the same source are
 * indistinguishable once installed, and the rolling release keeps its original publish date, so
 * "is this the new one?" had no answer on either side. Every field here comes from the installed
 * APK or from compile-time constants the signing pipeline pins — nothing is asserted by hand.
 *
 * Pure JVM on purpose (no `android.*` imports, no `BuildConfig` reference), so
 * `scripts/verify_pure_kotlin.sh` compiles and tests it without an Android SDK. The Android glue
 * that fills it in lives in `AndroidBuildStampReader.kt`.
 */
data class BuildStamp(
    val versionName: String,
    val versionCode: Int,
    val builtUtc: String,
    val pinnedSignerSha256: String,
    val installedSignerSha256: String?,
    val updatedOnDeviceEpochMillis: Long?,
) {
    /** Whether this APK really carries the project's pinned debug certificate. */
    val signerCheck: SignerCheck
        get() = SignerCheck.classify(
            installedSigner = installedSignerSha256,
            pinnedSigner = pinnedSignerSha256,
        )

    /** Card title, e.g. `Build 0.3.0-dev (code 3)`. */
    fun title(): String = "Build ${versionName.ifBlank { UNKNOWN }} (code $versionCode)"

    /** The lines shown under the title. Order is stable so tests and owners can rely on it. */
    fun lines(zone: ZoneId): List<String> = listOf(
        "Built ${builtUtc.ifBlank { UNKNOWN }}",
        signerLine(),
        "Updated on this phone ${updatedOnDevice(zone)}",
    )

    /** The signature line: a short digest plus a verdict, never a bare digest the owner must compare by eye. */
    fun signerLine(): String {
        val digest = installedSignerSha256
        return if (digest.isNullOrBlank()) {
            "Signed: could not read this APK's signature"
        } else {
            "Signed ${shortDigest(digest)} — ${signerCheck.explanation}"
        }
    }

    /** When Android last installed or updated this package on this device. */
    fun updatedOnDevice(zone: ZoneId): String {
        val millis = updatedOnDeviceEpochMillis ?: return UNKNOWN
        if (millis <= 0L) return UNKNOWN
        return DEVICE_TIME_FORMAT.withZone(zone).format(Instant.ofEpochMilli(millis))
    }

    private companion object {
        const val UNKNOWN = "unknown"

        val DEVICE_TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.US)
    }
}

/**
 * The verdict on the installed APK's signing certificate, compared with the digest pinned in
 * `android/debug-signer.sha256` and stamped into this build at compile time.
 */
enum class SignerCheck(val explanation: String) {
    PINNED("matches the project's pinned key"),
    DRIFT("does NOT match the project's pinned key"),
    NO_PIN("this build recorded no pinned key to compare against"),
    NO_SIGNATURE("signature unreadable"),
    ;

    companion object {
        fun classify(installedSigner: String?, pinnedSigner: String): SignerCheck = when {
            installedSigner.isNullOrBlank() -> NO_SIGNATURE
            pinnedSigner.isBlank() -> NO_PIN
            installedSigner.trim().equals(pinnedSigner.trim(), ignoreCase = true) -> PINNED
            else -> DRIFT
        }
    }
}

private val HEX_DIGITS = "0123456789abcdef".toCharArray()

/**
 * SHA-256 of a DER-encoded certificate as lowercase hex — the same form `apksigner
 * verify --print-certs` prints, CI asserts against, and `android/debug-signer.sha256` stores.
 */
fun signerSha256Hex(certificateDer: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(certificateDer)
    val out = CharArray(digest.size * 2)
    digest.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xFF
        out[index * 2] = HEX_DIGITS[value ushr 4]
        out[(index * 2) + 1] = HEX_DIGITS[value and 0x0F]
    }
    return String(out)
}

/** A full 64-character digest is not readable on a phone; the prefix is enough to compare. */
fun shortDigest(digest: String, keep: Int = 8): String {
    val trimmed = digest.trim()
    return when {
        trimmed.isEmpty() -> ""
        trimmed.length <= keep -> trimmed
        else -> trimmed.take(keep) + "…"
    }
}
