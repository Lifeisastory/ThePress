package com.aster.core.network.adapters.request

import com.aster.core.network.model.HttpRequestSpec
import com.aster.core.network.dto.HttpPayload
import okhttp3.OkHttpClient

class CookieBootstrapRequest(private val bootstrap: HttpRequestSpec) : SourceRequestStrategy {
    override suspend fun execute(client: OkHttpClient, request: HttpRequestSpec): List<HttpPayload> {
        val http = HttpRequestAdapter(client.newBuilder().cookieJar(SessionCookieJar()).build())
        http.execute(bootstrap)
        return listOf(http.execute(request))
    }
}
