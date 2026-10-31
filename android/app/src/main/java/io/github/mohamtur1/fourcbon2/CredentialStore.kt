package io.github.mohamtur1.fourcbon2

/**
 * Narrow local boundary for future user-entered service credentials.
 * Callers must never log, export, or place returned values in task histories.
 */
interface CredentialStore {
    fun save(slot: CredentialSlot, secret: CharArray)

    fun load(slot: CredentialSlot): CharArray?

    fun remove(slot: CredentialSlot)

    fun contains(slot: CredentialSlot): Boolean
}

class CredentialStoreException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
