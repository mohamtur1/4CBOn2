package io.github.mohamtur1.fourcbon2

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Encrypts future user-entered credentials at rest using a non-exportable AES key in
 * AndroidKeyStore. Plaintext is never written to SharedPreferences or logs.
 */
class AndroidKeyStoreCredentialStore(context: Context) : CredentialStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun save(slot: CredentialSlot, secret: CharArray) {
        require(secret.isNotEmpty()) { "Credential must not be empty." }
        val plaintext = secret.concatToString().toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val record = EncryptedCredentialRecord(
                formatVersion = FORMAT_VERSION,
                initializationVector = cipher.iv,
                ciphertext = cipher.doFinal(plaintext),
            )
            val persisted = preferences.edit()
                .putString(slot.storageKey, record.encode())
                .commit()
            check(persisted) { "Credential could not be saved." }
        } catch (exception: GeneralSecurityException) {
            throw CredentialStoreException("Credential storage is unavailable.", exception)
        } finally {
            plaintext.fill(0)
        }
    }

    override fun load(slot: CredentialSlot): CharArray? {
        val encoded = preferences.getString(slot.storageKey, null) ?: return null
        val plaintext: ByteArray
        try {
            val record = EncryptedCredentialRecord.decode(encoded)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), javax.crypto.spec.GCMParameterSpec(TAG_LENGTH_BITS, record.initializationVector))
            plaintext = cipher.doFinal(record.ciphertext)
        } catch (exception: GeneralSecurityException) {
            throw CredentialStoreException("Stored credential is unavailable. Remove it and enter it again.", exception)
        } catch (exception: IllegalArgumentException) {
            throw CredentialStoreException("Stored credential has an invalid format. Remove it and enter it again.", exception)
        }

        return try {
            String(plaintext, Charsets.UTF_8).toCharArray()
        } finally {
            plaintext.fill(0)
        }
    }

    override fun remove(slot: CredentialSlot) {
        val removed = preferences.edit().remove(slot.storageKey).commit()
        check(removed) { "Credential could not be removed." }
    }

    override fun contains(slot: CredentialSlot): Boolean = preferences.contains(slot.storageKey)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null)
        if (existingKey != null) {
            return existingKey as? SecretKey
                ?: throw CredentialStoreException("Credential key has an unexpected type.")
        }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "io.github.mohamtur1.fourcbon2.credential.master.v1"
        const val PREFERENCES_NAME = "credential_store_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = 1
        const val TAG_LENGTH_BITS = 128
    }
}
