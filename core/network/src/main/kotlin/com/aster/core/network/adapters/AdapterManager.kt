package com.aster.core.network.adapters

import com.aster.core.network.NetworkClient
import com.aster.core.network.adapters.mapping.JsonItemMapper
import com.aster.core.network.adapters.request.HttpRequestAdapter
import com.aster.core.network.dto.SourceItem
import com.aster.core.network.model.NetworkError
import com.aster.core.network.model.NetworkResult
import com.aster.core.network.model.ProcessingStage
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.networkRequest

/** 按来源定义调度通用工具，不包含任何平台分支 */
class AdapterManager(
    private val client: NetworkClient,
    definitions: List<SourceDefinition>
) {
    private val sources = definitions.associateBy { it.id }
    private val http = HttpRequestAdapter(client.httpClient)
    private val mapper = JsonItemMapper()

    init {
        require(sources.size == definitions.size) { "来源 ID 不能重复" }
    }

    suspend fun fetch(sourceId: String): NetworkResult<List<SourceItem>> {
        val source = sources[sourceId] ?: return NetworkResult.Failure(
            NetworkError.Processing(sourceId, ProcessingStage.SOURCE, "未注册来源：$sourceId")
        )
        return try {
            networkRequest {
                val response = http.execute(source.request)
                val document = source.extractor.extract(response, client.json)
                mapper.map(source.id, document, source.mapping, response.url)
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
    }
}
