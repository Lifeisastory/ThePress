package com.aster.core.network.adapters.mapping

import com.aster.core.network.adapters.AdapterException
import com.aster.core.network.dto.SourceItem
import com.aster.core.network.dto.SourceStockInfo
import com.aster.core.network.model.StockMapping
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
            // 配图是可选增强，字段异常不得使整条资讯或整个来源失败
            val image = (rule.imagePath?.read(item) as? JsonPrimitive)
                ?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { baseUrl.resolve(it) }
                ?.let { imageUrl ->
                    // 已验证 B 站图片 CDN 支持 HTTPS，避免 Android 明文请求限制
                    if (imageUrl.scheme == "http" && imageUrl.host.endsWith(".hdslb.com")) {
                        imageUrl.newBuilder().scheme("https").build()
                    } else imageUrl
                }?.toString()
            SourceItem(
                sourceId, id, title, url, rule.summaryPath?.let { text(item, it, false, index) }, image,
                stockInfo = rule.stock?.let { stockInfo(item, it) }
            )
        }.distinctBy { it.id }
        if (!rule.allowEmpty && result.isEmpty()) fail("来源不允许返回空列表")
        return result
    }

    // 行情是可选增强；不完整或非法数据不伪造为零，也不使整个来源失败
    private fun stockInfo(item: JsonElement, rule: StockMapping): SourceStockInfo? {
        val code = (rule.codePath.read(item) as? JsonPrimitive)
            ?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        fun number(path: JsonPath) = (path.read(item) as? JsonPrimitive)
            ?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }
        return SourceStockInfo(
            code = code,
            price = number(rule.pricePath)?.takeIf { it >= 0 } ?: return null,
            change = number(rule.changePath) ?: return null,
            changePercent = number(rule.changePercentPath) ?: return null
        )
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
