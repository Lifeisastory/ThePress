package com.aster.core.network.adapters.extract

import com.aster.core.network.adapters.AdapterException
import com.aster.core.network.dto.HttpPayload
import com.aster.core.network.model.ProcessingStage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** 提取明确起止标记之间的 JSON 文本；不执行 JavaScript，也不猜测页面中的数据位置 */
data class EmbeddedJsonExtractor(val startMarker: String, val endMarker: String) : ContentExtractor {
    init {
        require(startMarker.isNotEmpty() && endMarker.isNotEmpty()) { "内嵌 JSON 的起止标记不能为空" }
    }

    override fun extract(response: HttpPayload, json: Json): JsonElement {
        val start = response.body.indexOf(startMarker)
        if (start < 0) throw AdapterException(ProcessingStage.EXTRACTION, "未找到内嵌 JSON 起始标记")
        val contentStart = start + startMarker.length
        val end = response.body.indexOf(endMarker, contentStart)
        if (end < 0) throw AdapterException(ProcessingStage.EXTRACTION, "未找到内嵌 JSON 结束标记")
        return JsonExtractor.extract(response.copy(body = response.body.substring(contentStart, end)), json)
    }
}
