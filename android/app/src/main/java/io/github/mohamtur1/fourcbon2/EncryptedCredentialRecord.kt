package io.github.mohamtur1.fourcbon2

import java.util.Base64

/**
 * Versioned storage envelope. This contains only an IV and ciphertext, never plaintext.
 */
internal data class EncryptedCredentialRecord(
    val formatVersion: Int,
    val initializationVector: ByteArray,
    val ciphertext: ByteArray,
) {
    init {
        require(formatVersion == CURRENT_FORMAT_VERSION) { "Unsupported credential record version." }
        require(initializationVector.size == GCM_IV_LENGTH_BYTES) { "Invalid credential record IV." }
        require(ciphertext.isNotEmpty()) { "Credential ciphertext is empty." }
    }

    fun encode(): String = listOf(
        "v$formatVersion",
        Base64.getUrlEncoder().withoutPadding().encodeToString(initializationVector),
        Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext),
    ).joinToString(separator = ".")

    companion object {
        private const val CURRENT_FORMAT_VERSION = 1
        private const val GCM_IV_LENGTH_BYTES = 12

        fun decode(encoded: String): EncryptedCredentialRecord {
            val parts = encoded.split(".")
            require(parts.size == 3) { "Invalid credential record." }
            val version = parts[0].removePrefix("v").toIntOrNull()
                ?: throw IllegalArgumentException("Invalid credential record version.")
            return EncryptedCredentialRecord(
                formatVersion = version,
                initializationVector = Base64.getUrlDecoder().decode(parts[1]),
                ciphertext = Base64.getUrlDecoder().decode(parts[2]),
            )
        }
    }
}
