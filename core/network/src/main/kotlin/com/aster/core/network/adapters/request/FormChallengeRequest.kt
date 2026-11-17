package com.aster.core.network.adapters.request

import com.aster.core.network.dto.HttpPayload
import com.aster.core.network.model.HttpRequestSpec
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.milliseconds

/** 有限次数的表单确认流程；平台配置只负责识别响应并生成表单 */
class FormChallengeRequest(
    private val challenge: (HttpPayload) -> HttpRequestSpec?,
    private val attempts: Int = 2,
    private val delayMillis: Long = 600
) : SourceRequestStrategy {
    init {
        require(attempts in 1..3 && delayMillis >= 0)
    }

    override suspend fun execute(client: OkHttpClient, request: HttpRequestSpec): List<HttpPayload> {
        val http = HttpRequestAdapter(client.newBuilder().cookieJar(SessionCookieJar()).build())
        var response = http.execute(request)
        repeat(attempts) {
            val form = challenge(response) ?: return listOf(response)
            http.execute(form)
            delay(delayMillis.milliseconds)
            response = http.execute(request)
        }
        return listOf(response)
    }
}
