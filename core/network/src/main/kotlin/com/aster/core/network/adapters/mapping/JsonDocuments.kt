package com.aster.core.network.adapters.mapping

import com.aster.core.network.adapters.AdapterException
import com.aster.core.network.model.ProcessingStage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.jsoup.Jsoup

/** 来源转换规则共用的严格读取、合并和文本清理工具 */
object JsonDocuments {
    fun array(document: JsonElement, path: String = ""): List<JsonObject> {
        val items = JsonPath(path).read(document) as? JsonArray ?: fail("列表路径 $path 不存在或不是数组")
        return items.mapIndexed { index, item -> item as? JsonObject ?: fail("第 $index 条记录不是对象") }
    }

    fun merge(documents: List<JsonElement>, path: String): List<JsonObject> = documents.flatMap { array(it, path) }

    fun text(document: JsonElement, vararg paths: String): String? {
        paths.forEach { path ->
            val value = JsonPath(path).read(document)
            if (value != null && value != JsonNull) {
                if (value !is JsonPrimitive || (!value.isString && value.booleanOrNull != null)) fail("字段 $path 不是字符串或数字")
                if (value.content.isNotBlank()) return value.content.trim()
            }
        }
        return null
    }

    fun requireText(document: JsonElement, vararg paths: String): String = text(document, *paths) ?: fail("缺少字段 ${paths.joinToString()}")

    fun plainText(html: String): String = Jsoup.parseBodyFragment(html).text()

    fun fail(message: String): Nothing = throw AdapterException(ProcessingStage.MAPPING, message)
}
