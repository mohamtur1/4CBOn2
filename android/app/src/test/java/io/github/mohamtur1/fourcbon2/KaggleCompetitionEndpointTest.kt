package io.github.mohamtur1.fourcbon2

import org.junit.Assert.assertEquals
import org.junit.Test

class KaggleCompetitionEndpointTest {
    @Test
    fun firstReadRouteUsesTheFixedCompetitionListEndpoint() {
        assertEquals(
            "https://www.kaggle.com/api/v1/competitions/list?group=general",
            KaggleCompetitionEndpoint.listUrl().toString(),
        )
    }
}
