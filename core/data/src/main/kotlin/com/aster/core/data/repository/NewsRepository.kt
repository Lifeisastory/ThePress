package com.aster.core.data.repository

import com.aster.core.data.model.NewsArticle
import com.aster.core.data.model.NewsFailureReason
import com.aster.core.data.model.NewsLoadResult
import com.aster.core.data.model.NewsSource
import com.aster.core.network.NetworkClient
import com.aster.core.network.adapters.AdapterManager
import com.aster.core.network.model.NetworkError
import com.aster.core.network.model.NetworkResult
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.sources.BuiltInSources

/** 将来源适配结果转换为应用数据，调用方无需依赖网络 DTO */
class NewsRepository(
    client: NetworkClient,
    definitions: List<SourceDefinition> = BuiltInSources.all,
    private val onNetworkError: (sourceId: String, error: NetworkError) -> Unit = { _, _ -> }
) {
    private val adapters = AdapterManager(client, definitions)
    val sources: List<NewsSource> = definitions.map { NewsSource(it.id, it.name) }

    suspend fun fetch(sourceId: String): NewsLoadResult = when (val result = adapters.fetch(sourceId)) {
        is NetworkResult.Success -> NewsLoadResult.Success(
            result.data.map { NewsArticle(it.sourceId, it.id, it.title, it.url, it.summary) }
        )
        is NetworkResult.Failure -> {
            // 转换为界面可用的失败类型之前，将原始异常交给可选的诊断回调
            onNetworkError(sourceId, result.error)
            when (val error = result.error) {
                is NetworkError.Connection -> NewsLoadResult.Failure(NewsFailureReason.CONNECTION)
                is NetworkError.Timeout -> NewsLoadResult.Failure(NewsFailureReason.TIMEOUT)
                is NetworkError.Http -> NewsLoadResult.Failure(NewsFailureReason.HTTP, error.code)
                is NetworkError.Serialization, is NetworkError.Processing ->
                    NewsLoadResult.Failure(NewsFailureReason.INVALID_DATA)
            }
        }
    }
}
