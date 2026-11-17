package com.aster.core.network.model

import kotlinx.serialization.json.JsonElement
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl

/** 请求配置；query 通过 HttpUrl 编码，headers 只作用于本次请求 */
data class HttpRequestSpec(
    val url: HttpUrl,
    val method: HttpMethod = HttpMethod.GET,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val jsonBody: JsonElement? = null,
    val maxResponseBytes: Long = 2L * 1024 * 1024
) {
    init {
        require(method != HttpMethod.GET || jsonBody == null) { "GET 请求不能携带 JSON 正文" }
        require(maxResponseBytes in 1..Int.MAX_VALUE.toLong()) { "响应大小限制必须是正整数且不超过 Int.MAX_VALUE" }
        headers.toHeaders()
    }
}
