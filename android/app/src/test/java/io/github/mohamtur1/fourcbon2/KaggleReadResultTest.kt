package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Test

class KaggleReadResultTest {
    @Test
    fun knownHttpStatesMapToHonestReadOutcomes() {
        assertEquals(KaggleReadResult.CompetitionListVisible, KaggleReadResult.fromHttpResponse(200))
        assertEquals(KaggleReadResult.AuthenticationRequired, KaggleReadResult.fromHttpResponse(401))
        assertEquals(KaggleReadResult.AccessForbidden, KaggleReadResult.fromHttpResponse(403))
        assertEquals(KaggleReadResult.RateLimited, KaggleReadResult.fromHttpResponse(429))
    }
}
