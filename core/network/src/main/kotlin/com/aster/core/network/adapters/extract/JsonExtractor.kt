package com.aster.core.network.adapters.extract

import com.aster.core.network.adapters.AdapterException
import com.aster.core.network.dto.HttpPayload
import com.aster.core.model.ProcessingStage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** 列表来源的 JSON 文档必须以对象或数组为根节点 */
object JsonExtractor : ContentExtractor {
    override fun extract(response: HttpPayload, json: Json): JsonElement = try {
        json.parseToJsonElement(response.body).also {
            if (it !is JsonObject && it !is JsonArray) {
                throw AdapterException(ProcessingStage.EXTRACTION, "JSON 文档根节点必须是对象或数组")
            }
        }
    } catch (error: SerializationException) {
        throw AdapterException(ProcessingStage.EXTRACTION, "响应不是有效的 JSON", error)
    }
}
