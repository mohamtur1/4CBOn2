package io.github.mohamtur1.fourcbon2.kaggle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KaggleStatusAdapterTest {

    @Test
    fun withoutCredentialReportsUnauthorized() {
        val adapter = KaggleStatusAdapter()
        val snapshot = adapter.currentStatus(credentialPresent = false)
        assertEquals(KaggleStatusAdapter.Status.UNAUTHORIZED, snapshot.status)
        assertNull(snapshot.lastSyncEpochMs)
        assertEquals(
            "Kaggle read requires an authorized account. The fixed competition-list endpoint returned HTTP 401 without a credential.",
            snapshot.detail,
        )
    }

    @Test
    fun withCredentialReportsAuthorizedAndRecordsSyncTime() {
        var now = 1234L
        val adapter = KaggleStatusAdapter()
        val snapshot = adapter.currentStatus(credentialPresent = true, clock = { now })
        assertEquals(KaggleStatusAdapter.Status.AUTHORIZED, snapshot.status)
        assertEquals(1234L, snapshot.lastSyncEpochMs)
    }

    @Test
    fun lastSyncTimeIsCarriedWhenKnown() {
        val adapter = KaggleStatusAdapter()
        val snapshot = adapter.currentStatus(credentialPresent = false, lastSyncEpochMs = 555L)
        assertEquals(555L, snapshot.lastSyncEpochMs)
    }
}
