package io.github.mohamtur1.fourcbon2

import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The build stamp is the owner's only way to answer "which build is on my phone?" from the device
 * itself, so it must never invent an answer: an unreadable signature or a missing pin has to say
 * so plainly rather than render as a confident-looking blank.
 */
class BuildStampTest {
    private val pinned = "ea1f65ed3e32acafabeabdc8bd9af697520929fbe9b8849eb612efbf0fe3b316"

    private fun stamp(
        installedSigner: String? = pinned,
        pin: String = pinned,
        updatedMillis: Long? = 1_794_583_800_000L, // 2026-11-13T15:30:00Z
    ) = BuildStamp(
        versionName = "0.3.0-dev",
        versionCode = 3,
        builtUtc = "2026-11-13 14:31 UTC",
        pinnedSignerSha256 = pin,
        installedSignerSha256 = installedSigner,
        updatedOnDeviceEpochMillis = updatedMillis,
    )

    @Test
    fun certificateDigestMatchesTheWellKnownSha256TestVector() {
        // RFC 6234 / FIPS 180-4 vector, so a broken hex encoding cannot pass by accident.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            signerSha256Hex("abc".toByteArray(Charsets.UTF_8)),
        )
        val digest = signerSha256Hex(ByteArray(792) { it.toByte() })
        assertEquals(64, digest.length)
        assertTrue("digest must be lowercase hex: $digest", digest.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun pinnedCertificateIsRecognisedAndAnyOtherKeyIsCalledDrift() {
        assertEquals(SignerCheck.PINNED, stamp().signerCheck)
        // The package manager and the pin file may disagree on case or padding, not on identity.
        assertEquals(SignerCheck.PINNED, stamp(installedSigner = " $pinned ".uppercase()).signerCheck)

        val reSigned = stamp(installedSigner = "d2ffb5ea8d17a3a966dedfeb62b1b43e2bcf5408a570d48dac318be8139be7c7")
        assertEquals(SignerCheck.DRIFT, reSigned.signerCheck)
        assertTrue(
            "drift must be stated, not implied: ${reSigned.signerLine()}",
            reSigned.signerLine().contains("does NOT match"),
        )
    }

    @Test
    fun stampNamesTheVersionTheBuildTimeAndTheDeviceUpdateTimeInTheOwnersZone() {
        val stamp = stamp()
        assertEquals("Build 0.3.0-dev (code 3)", stamp.title())
        assertEquals(
            listOf(
                "Built 2026-11-13 14:31 UTC",
                "Signed ea1f65ed… — matches the project's pinned key",
                "Updated on this phone 13 Nov 2026, 15:30",
            ),
            stamp.lines(ZoneOffset.UTC),
        )
        // The install time is the phone's, not UTC: the same instant reads differently in Tokyo.
        assertEquals(
            "14 Nov 2026, 00:30",
            stamp.updatedOnDevice(ZoneId.of("Asia/Tokyo")),
        )
    }

    @Test
    fun unreadableSignatureMissingPinAndMissingInstallTimeAreReportedNotInvented() {
        val noSignature = stamp(installedSigner = null)
        assertEquals(SignerCheck.NO_SIGNATURE, noSignature.signerCheck)
        assertEquals("Signed: could not read this APK's signature", noSignature.signerLine())

        val noPin = stamp(pin = "")
        assertEquals(SignerCheck.NO_PIN, noPin.signerCheck)
        assertTrue(noPin.signerLine().contains("no pinned key"))

        val noInstallTime = stamp(updatedMillis = null)
        assertEquals("unknown", noInstallTime.updatedOnDevice(ZoneOffset.UTC))
        assertEquals("unknown", stamp(updatedMillis = 0L).updatedOnDevice(ZoneOffset.UTC))

        val blankVersion = stamp().copy(versionName = "", builtUtc = "")
        assertEquals("Build unknown (code 3)", blankVersion.title())
        assertEquals("Built unknown", blankVersion.lines(ZoneOffset.UTC).first())
    }

    @Test
    fun digestsAreShortenedForAPhoneScreenWithoutLosingThePrefix() {
        assertEquals("ea1f65ed…", shortDigest(pinned))
        // A truncated digest always keeps the ellipsis: it must look truncated, not look complete.
        assertEquals("ea1f…", shortDigest(pinned, keep = 4))
        assertEquals("abcd", shortDigest("abcd"))
        assertEquals("", shortDigest("   "))
    }
}
