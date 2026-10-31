package io.github.mohamtur1.fourcbon2

import java.net.URL

/** Fixed first read endpoint; arbitrary URLs are not accepted by the adapter. */
object KaggleCompetitionEndpoint {
    fun listUrl(): URL = URL("https://www.kaggle.com/api/v1/competitions/list?group=general")
}
