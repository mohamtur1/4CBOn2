package io.github.mohamtur1.fourcbon2

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build

/**
 * Fills in a [BuildStamp] from the installed package and the compile-time constants.
 *
 * The version identity and the pinned digest come from `BuildConfig`, which
 * `android/app/build.gradle.kts` stamps from the same files CI signs and verifies with, so the
 * phone and the release page cannot disagree about what "pinned" means. The signature actually
 * present on the installed APK comes from the package manager — that is the half worth checking,
 * because it is the half a repackaged or re-signed APK would change.
 *
 * Nothing here throws: an unreadable certificate degrades to a null digest, which [BuildStamp]
 * reports honestly instead of crashing the Home screen.
 */
@Suppress("DEPRECATION") // PackageManager.GET_SIGNATURES / PackageInfo.signatures are the only option below API 28.
fun readBuildStamp(context: Context): BuildStamp {
    val packageInfo: PackageInfo? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        context.packageManager.getPackageInfo(context.packageName, flags)
    }.getOrNull()

    return BuildStamp(
        versionName = BuildConfig.VERSION_NAME ?: "unknown",
        versionCode = BuildConfig.VERSION_CODE,
        builtUtc = BuildConfig.BUILT_UTC,
        pinnedSignerSha256 = BuildConfig.SIGNER_SHA256_PIN,
        installedSignerSha256 = packageInfo?.let { installedSignerSha256(it) },
        updatedOnDeviceEpochMillis = packageInfo?.lastUpdateTime?.takeIf { it > 0L },
    )
}

/** SHA-256 of the certificate this APK was actually signed with, or null if it cannot be read. */
private fun installedSignerSha256(packageInfo: PackageInfo): String? {
    val certificate: Signature? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val signingInfo = packageInfo.signingInfo ?: return null
        // A rotation would present several signers; this project has never rotated, and the first
        // entry is the one Android verified the APK against.
        val signers = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
        signers?.firstOrNull()
    } else {
        packageInfo.signatures?.firstOrNull()
    }
    val encoded: ByteArray = certificate?.toByteArray() ?: return null
    return runCatching { signerSha256Hex(encoded) }.getOrNull()
}
