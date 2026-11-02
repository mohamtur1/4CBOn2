package io.github.mohamtur1.fourcbon2

/**
 * Division 09a: Local-only credential validation, normalization, and Keystore storage wiring.
 *
 * Distinguishes GitHub fine-grained PATs, Kaggle Bearer/KGAT tokens, and Kaggle legacy
 * username:key (`kaggle.json`) pairs so incompatible token formats are never conflated.
 * Never logs, echoes, or retains plaintext secrets in memory after saving.
 */
sealed interface CredentialSaveOutcome {
    data class Saved(val slot: CredentialSlot) : CredentialSaveOutcome
    data object RejectedEmpty : CredentialSaveOutcome
    data class RejectedInvalidFormat(val guidance: String) : CredentialSaveOutcome
    data class StorageUnavailable(val message: String) : CredentialSaveOutcome
}

enum class CredentialPresence {
    CONFIGURED,
    NOT_CONFIGURED,
    STORAGE_UNAVAILABLE,
}

class CredentialEntryController(
    private val credentialStore: CredentialStore,
) {
    fun presenceOf(slot: CredentialSlot): CredentialPresence = try {
        if (credentialStore.contains(slot)) {
            CredentialPresence.CONFIGURED
        } else {
            CredentialPresence.NOT_CONFIGURED
        }
    } catch (_: CredentialStoreException) {
        CredentialPresence.STORAGE_UNAVAILABLE
    }

    fun configuredSlots(): Set<CredentialSlot> =
        CredentialSlot.entries.filterTo(linkedSetOf()) { presenceOf(it) == CredentialPresence.CONFIGURED }

    /**
     * Validates and saves a user-entered secret into [credentialStore].
     * Always zeroes [rawInput] and any intermediate normalized buffer before returning.
     */
    fun saveUserSecret(slot: CredentialSlot, rawInput: CharArray): CredentialSaveOutcome {
        var normalized: CharArray? = null
        return try {
            val trimmed = trimCharArray(rawInput)
            if (trimmed.isEmpty()) {
                return CredentialSaveOutcome.RejectedEmpty
            }

            when (val check = normalizeAndValidate(slot, trimmed)) {
                is ValidationResult.Invalid -> {
                    trimmed.fill('\u0000')
                    CredentialSaveOutcome.RejectedInvalidFormat(check.guidance)
                }
                is ValidationResult.Valid -> {
                    trimmed.fill('\u0000')
                    normalized = check.secret
                    credentialStore.save(slot, check.secret)
                    CredentialSaveOutcome.Saved(slot)
                }
            }
        } catch (ex: CredentialStoreException) {
            CredentialSaveOutcome.StorageUnavailable(
                ex.message ?: "AndroidKeyStore credential storage is unavailable on this device.",
            )
        } finally {
            rawInput.fill('\u0000')
            normalized?.fill('\u0000')
        }
    }

    fun clearSlot(slot: CredentialSlot): Boolean = try {
        credentialStore.remove(slot)
        true
    } catch (_: CredentialStoreException) {
        false
    }

    private sealed interface ValidationResult {
        data class Valid(val secret: CharArray) : ValidationResult
        data class Invalid(val guidance: String) : ValidationResult
    }

    private fun normalizeAndValidate(slot: CredentialSlot, trimmed: CharArray): ValidationResult {
        val text = trimmed.concatToString()
        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            return ValidationResult.Invalid("Paste the token value itself, not a website URL.")
        }

        return when (slot) {
            CredentialSlot.GITHUB_FINE_GRAINED_PAT -> validateGitHubPat(text)
            CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN -> validateKaggleBearer(text)
            CredentialSlot.KAGGLE_API_TOKEN -> validateKaggleApiPair(text)
        }
    }

    private fun validateGitHubPat(text: String): ValidationResult {
        if (text.any { it.isWhitespace() || it.code < 0x21 || it.code > 0x7E }) {
            return ValidationResult.Invalid("GitHub token must not contain spaces or line breaks.")
        }
        if (text.length < 20) {
            return ValidationResult.Invalid("GitHub token is too short. Use a fine-grained PAT (github_pat_...).")
        }
        return ValidationResult.Valid(text.toCharArray())
    }

    private fun validateKaggleBearer(text: String): ValidationResult {
        if (text.startsWith("{")) {
            return ValidationResult.Invalid(
                "This looks like kaggle.json. Switch slot to 'Kaggle API Key (kaggle.json)' instead of Bearer token.",
            )
        }
        if (text.contains(':') && !text.startsWith("KGAT_")) {
            return ValidationResult.Invalid(
                "This looks like a username:key pair. Switch slot to 'Kaggle API Key (kaggle.json)' for Basic auth.",
            )
        }
        if (text.any { it.isWhitespace() || it.code < 0x21 || it.code > 0x7E }) {
            return ValidationResult.Invalid("Bearer token must not contain spaces, quotes, or line breaks.")
        }
        if (text.length < 16) {
            return ValidationResult.Invalid("Kaggle Bearer / KGAT token is too short.")
        }
        return ValidationResult.Valid(text.toCharArray())
    }

    private fun validateKaggleApiPair(text: String): ValidationResult {
        if (text.startsWith("KGAT_")) {
            return ValidationResult.Invalid(
                "KGAT_ tokens use Bearer authentication. Select 'Kaggle Bearer / KGAT Token' instead.",
            )
        }
        val (username, key) = if (text.startsWith("{")) {
            val obj = try {
                JsonCodec.parseObject(text)
            } catch (_: IllegalArgumentException) {
                return ValidationResult.Invalid("Malformed kaggle.json object. Expected {\"username\":\"...\",\"key\":\"...\"}.")
            }
            val u = obj.string("username")?.trim().orEmpty()
            val k = obj.string("key")?.trim().orEmpty()
            u to k
        } else {
            val colonIdx = text.indexOf(':')
            if (colonIdx <= 0 || colonIdx == text.lastIndex) {
                return ValidationResult.Invalid(
                    "Enter either kaggle.json ({\"username\":\"...\",\"key\":\"...\"}) or username:key.",
                )
            }
            text.substring(0, colonIdx).trim() to text.substring(colonIdx + 1).trim()
        }

        if (username.isEmpty() || username.any { it.isWhitespace() || it == ':' }) {
            return ValidationResult.Invalid("Kaggle username is invalid.")
        }
        if (key.length < 16 || key.any { it.isWhitespace() }) {
            return ValidationResult.Invalid("Kaggle API key must be at least 16 non-whitespace characters.")
        }
        return ValidationResult.Valid("$username:$key".toCharArray())
    }

    private fun trimCharArray(input: CharArray): CharArray {
        var start = 0
        var end = input.size - 1
        while (start <= end && input[start].isWhitespace()) start++
        while (end >= start && input[end].isWhitespace()) end--
        return if (start > end) CharArray(0) else input.copyOfRange(start, end + 1)
    }
}

/**
 * Non-persistent in-memory [CredentialStore] used as a safe fallback in previews or tests
 * when AndroidKeyStore hardware/OS providers are absent.
 */
class InMemoryCredentialStore : CredentialStore {
    private val map = LinkedHashMap<CredentialSlot, CharArray>()

    override fun save(slot: CredentialSlot, secret: CharArray) {
        require(secret.isNotEmpty()) { "Credential must not be empty." }
        map.put(slot, secret.copyOf())?.fill('\u0000')
    }

    override fun load(slot: CredentialSlot): CharArray? = map[slot]?.copyOf()

    override fun remove(slot: CredentialSlot) {
        map.remove(slot)?.fill('\u0000')
    }

    override fun contains(slot: CredentialSlot): Boolean = map.containsKey(slot)
}
