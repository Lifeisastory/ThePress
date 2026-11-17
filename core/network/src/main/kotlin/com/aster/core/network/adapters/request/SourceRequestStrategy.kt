package com.aster.core.network.adapters.request

import com.aster.core.network.dto.HttpPayload
import com.aster.core.network.model.HttpRequestSpec
import okhttp3.OkHttpClient

/** 请求阶段的组合点；始终通过共享 HTTP 工具执行，沿用取消、超时和正文大小限制 */
fun interface SourceRequestStrategy {
    suspend fun execute(client: OkHttpClient, request: HttpRequestSpec): List<HttpPayload>

    object Direct : SourceRequestStrategy {
        override suspend fun execute(client: OkHttpClient, request: HttpRequestSpec) =
            listOf(HttpRequestAdapter(client).execute(request))
    }
}
