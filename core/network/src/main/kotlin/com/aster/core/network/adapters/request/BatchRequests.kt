package com.aster.core.network.adapters.request

import com.aster.core.network.model.HttpRequestSpec
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient

/** 主请求与附加请求全部成功才进入提取阶段；返回顺序与配置一致 */
class BatchRequests(private val additionalRequests: List<HttpRequestSpec>) : SourceRequestStrategy {
    override suspend fun execute(client: OkHttpClient, request: HttpRequestSpec) = coroutineScope {
        val http = HttpRequestAdapter(client)
        (listOf(request) + additionalRequests).map { spec -> async { http.execute(spec) } }.awaitAll()
    }
}
