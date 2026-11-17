package com.aster.core.network.adapters.extract

import com.aster.core.network.dto.HttpPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** 提取器统一输出 JSON 树；后续 HTML/RSS 工具也可输出记录数组供映射器复用 */
fun interface ContentExtractor {
    fun extract(response: HttpPayload, json: Json): JsonElement
}
