package com.aster.core.network.model

import com.aster.core.network.adapters.extract.ContentExtractor
import com.aster.core.network.adapters.extract.JsonExtractor
import com.aster.core.network.adapters.request.SourceRequestStrategy
import kotlinx.serialization.json.JsonElement

/** 平台规则定义；请求、提取和映射能力独立组合 */
data class SourceDefinition(
    val id: String,
    val name: String,
    val request: HttpRequestSpec,
    val mapping: ItemMapping,
    val extractor: ContentExtractor = JsonExtractor,
    val requestStrategy: SourceRequestStrategy = SourceRequestStrategy.Direct,
    val prepareRequest: (HttpRequestSpec) -> HttpRequestSpec = { it },
    val transform: (List<JsonElement>) -> JsonElement = { it.single() },
    val fallback: SourceDefinition? = null
) {
    init {
        require(id.isNotBlank()) { "来源 ID 不能为空" }
        require(name.isNotBlank()) { "来源名称不能为空" }
    }
}
