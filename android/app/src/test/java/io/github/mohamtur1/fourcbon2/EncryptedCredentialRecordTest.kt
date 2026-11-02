package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EncryptedCredentialRecordTest {
    @Test
    fun recordRoundTripsWithoutPlaintext() {
        val original = EncryptedCredentialRecord(
            formatVersion = 1,
            initializationVector = ByteArray(12) { it.toByte() },
            ciphertext = byteArrayOf(9, 8, 7, 6),
        )

        val decoded = EncryptedCredentialRecord.decode(original.encode())

        assertEquals(1, decoded.formatVersion)
        assertArrayEquals(original.initializationVector, decoded.initializationVector)
        assertArrayEquals(original.ciphertext, decoded.ciphertext)
        assert(!original.encode().contains("github"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedRecordIsRejected() {
        EncryptedCredentialRecord.decode("not-a-record")
    }
}
