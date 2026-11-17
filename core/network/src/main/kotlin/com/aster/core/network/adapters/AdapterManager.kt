package com.aster.core.network.adapters

import com.aster.core.network.NetworkClient
import com.aster.core.network.adapters.mapping.JsonItemMapper
import com.aster.core.network.dto.SourceItem
import com.aster.core.network.model.NetworkError
import com.aster.core.network.model.NetworkResult
import com.aster.core.network.model.ProcessingStage
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.networkRequest
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** 按来源定义调度通用工具，不包含任何平台分支 */
class AdapterManager(
    private val client: NetworkClient,
    definitions: List<SourceDefinition>
) {
    private val sources = definitions.associateBy { it.id }
    private val mapper = JsonItemMapper()

    init {
        require(sources.size == definitions.size) { "来源 ID 不能重复" }
    }

    suspend fun fetch(sourceId: String): NetworkResult<List<SourceItem>> {
        val source = sources[sourceId] ?: return NetworkResult.Failure(
            NetworkError.Processing(sourceId, ProcessingStage.SOURCE, "未注册来源：$sourceId")
        )
        return fetchDefinition(source, sourceId)
    }

    private suspend fun fetchDefinition(source: SourceDefinition, sourceId: String): NetworkResult<List<SourceItem>> {
        val result = try {
            networkRequest {
                val responses = source.requestStrategy.execute(client.httpClient, source.prepareRequest(source.request))
                if (responses.isEmpty()) throw AdapterException(ProcessingStage.REQUEST, "请求流程没有返回响应")
                val document = source.transform(responses.map { source.extractor.extract(it, client.json) })
                mapper.map(sourceId, document, source.mapping, responses.first().url)
            }
        } catch (error: AdapterException) {
            NetworkResult.Failure(
                NetworkError.Processing(
                    sourceId = sourceId,
                    stage = error.stage,
                    message = error.message ?: "响应处理失败",
                    cause = error
                )
            )
        }
        coroutineContext.ensureActive()
        return if (result is NetworkResult.Failure && source.fallback != null) {
            fetchDefinition(source.fallback, sourceId)
        } else result
    }
}
