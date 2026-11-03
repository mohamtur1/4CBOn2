package io.github.mohamtur1.fourcbon2

import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialEntryAndKaggleAuthTest {
    @Test
    fun emptyOrUrlInputsAreRejectedAndInputBufferIsZeroed() {
        val store = InMemoryCredentialStore()
        val controller = CredentialEntryController(store)

        val emptyBuf = "   ".toCharArray()
        assertEquals(
            CredentialSaveOutcome.RejectedEmpty,
            controller.saveUserSecret(CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN, emptyBuf),
        )
        assertTrue(emptyBuf.all { it == '\u0000' })

        val urlBuf = "https://www.kaggle.com/settings".toCharArray()
        val urlRes = controller.saveUserSecret(CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN, urlBuf)
        assertTrue(urlRes is CredentialSaveOutcome.RejectedInvalidFormat)
        assertTrue(urlBuf.all { it == '\u0000' })
    }

    @Test
    fun kaggleJsonAndBearerFormatsAreStrictlyDistinguished() {
        val store = InMemoryCredentialStore()
        val controller = CredentialEntryController(store)

        val jsonIntoBearer = """{"username":"alice","key":"0123456789abcdef0123456789abcdef"}""".toCharArray()
        val wrongSlot = controller.saveUserSecret(CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN, jsonIntoBearer)
        assertTrue(wrongSlot is CredentialSaveOutcome.RejectedInvalidFormat)

        val kgatIntoBasic = "KGAT_abcdefghijklmnopqrstuvwx".toCharArray()
        val wrongBasic = controller.saveUserSecret(CredentialSlot.KAGGLE_API_TOKEN, kgatIntoBasic)
        assertTrue(wrongBasic is CredentialSaveOutcome.RejectedInvalidFormat)

        val validJson = """{"username":"alice","key":"0123456789abcdef0123456789abcdef"}""".toCharArray()
        val saved = controller.saveUserSecret(CredentialSlot.KAGGLE_API_TOKEN, validJson)
        assertEquals(CredentialSaveOutcome.Saved(CredentialSlot.KAGGLE_API_TOKEN), saved)
        assertTrue(validJson.all { it == '\u0000' })
        assertEquals(CredentialPresence.CONFIGURED, controller.presenceOf(CredentialSlot.KAGGLE_API_TOKEN))
        assertEquals("alice:0123456789abcdef0123456789abcdef", store.load(CredentialSlot.KAGGLE_API_TOKEN)!!.concatToString())
    }

    @Test
    fun kaggleClientUsesBearerOrBasicDependingOnConfiguredSlot() {
        val store = InMemoryCredentialStore()
        val controller = CredentialEntryController(store)
        var capturedAuth: String? = null

        controller.saveUserSecret(
            CredentialSlot.KAGGLE_API_TOKEN,
            "alice:0123456789abcdef0123456789abcdef".toCharArray(),
        )
        val client = KaggleCompetitionReadClient(
            credentialStore = store,
            connectionFactory = { url ->
                object : HttpURLConnection(url) {
                    override fun disconnect() = Unit
                    override fun usingProxy(): Boolean = false
                    override fun connect() = Unit
                    override fun setRequestProperty(key: String?, value: String?) {
                        if (key == "Authorization") capturedAuth = value
                    }
                    override fun getResponseCode(): Int = 200
                }
            },
        )

        assertEquals(KaggleReadResult.CompetitionListVisible, client.probeAuthorizedCompetitionList())
        val expectedBasic = Base64.getEncoder()
            .encodeToString("alice:0123456789abcdef0123456789abcdef".toByteArray(Charsets.UTF_8))
        assertEquals("Basic $expectedBasic", capturedAuth)

        // When Bearer token is also present, Bearer takes precedence.
        controller.saveUserSecret(
            CredentialSlot.KAGGLE_BEARER_ACCESS_TOKEN,
            "KGAT_1234567890abcdefghij".toCharArray(),
        )
        assertEquals(KaggleReadResult.CompetitionListVisible, client.probeAuthorizedCompetitionList())
        assertEquals("Bearer KGAT_1234567890abcdefghij", capturedAuth)
    }
}
