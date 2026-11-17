package com.aster.core.network.model

import kotlinx.serialization.json.JsonElement
import java.nio.charset.Charset
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl

/** 请求配置；query 通过 HttpUrl 编码，headers 只作用于本次请求 */
data class HttpRequestSpec(
    val url: HttpUrl,
    val method: HttpMethod = HttpMethod.GET,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val jsonBody: JsonElement? = null,
    val maxResponseBytes: Long = 2L * 1024 * 1024,
    val formBody: Map<String, String>? = null,
    val responseCharset: Charset? = null,
    val acceptedStatusCodes: Set<Int> = emptySet()
) {
    init {
        require(method != HttpMethod.GET || jsonBody == null) { "GET 请求不能携带 JSON 正文" }
        require(method != HttpMethod.GET || formBody == null) { "GET 请求不能携带表单正文" }
        require(jsonBody == null || formBody == null) { "JSON 和表单正文不能同时设置" }
        require(maxResponseBytes in 1..Int.MAX_VALUE.toLong()) { "响应大小限制必须是正整数且不超过 Int.MAX_VALUE" }
        headers.toHeaders()
    }
}
