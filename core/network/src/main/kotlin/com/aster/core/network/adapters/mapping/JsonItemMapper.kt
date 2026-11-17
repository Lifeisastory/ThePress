package com.aster.core.network.adapters.mapping

import com.aster.core.network.adapters.AdapterException
import com.aster.core.network.dto.SourceItem
import com.aster.core.network.model.ItemMapping
import com.aster.core.network.model.ProcessingStage
import com.aster.core.network.model.UrlMapping
import java.net.URLEncoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class JsonItemMapper {
    fun map(sourceId: String, document: JsonElement, rule: ItemMapping, baseUrl: HttpUrl): List<SourceItem> {
        rule.requiredRootValues.forEach {
            if (it.path.read(document) != it.value) fail("响应字段 ${it.path} 与预期不符")
        }
        val items = rule.itemsPath.read(document) as? JsonArray
            ?: fail("列表路径 ${rule.itemsPath} 不存在或不是数组")
        val result = items.mapIndexedNotNull { index, item ->
            if (item !is JsonObject) fail("第 $index 条记录不是对象")
            if (rule.excludedItems.any { it.path.read(item) == it.value }) return@mapIndexedNotNull null
            val id = text(item, rule.idPath, true, index)!!
            val title = text(item, rule.titlePath, true, index)!!
            val url = when (val mapping = rule.url) {
                is UrlMapping.Field -> {
                    val value = text(item, mapping.path, true, index)!!
                    baseUrl.resolve(value)?.toString() ?: fail("第 $index 条记录的链接无效")
                }
                is UrlMapping.Template -> {
                    val encodedId = URLEncoder.encode(id, "UTF-8").replace("+", "%20")
                    mapping.value.replace("{id}", encodedId).toHttpUrlOrNull()?.toString()
                        ?: fail("第 $index 条记录的链接无效")
                }
            }
            SourceItem(sourceId, id, title, url, rule.summaryPath?.let { text(item, it, false, index) })
        }.distinctBy { it.id }
        if (!rule.allowEmpty && result.isEmpty()) fail("来源不允许返回空列表")
        return result
    }

    private fun text(item: JsonElement, path: JsonPath, required: Boolean, index: Int): String? {
        val value = path.read(item)
        if (value == null || value == JsonNull) {
            if (required) fail("第 $index 条记录缺少字段 $path")
            return null
        }
        if (value !is JsonPrimitive || (!value.isString && value.booleanOrNull != null)) {
            fail("第 $index 条记录的字段 $path 不是字符串或数字")
        }
        val text = value.content.trim()
        if (text.isEmpty()) {
            if (required) fail("第 $index 条记录的字段 $path 为空")
            return null
        }
        return text
    }

    private fun fail(message: String): Nothing = throw AdapterException(ProcessingStage.MAPPING, message)
}
