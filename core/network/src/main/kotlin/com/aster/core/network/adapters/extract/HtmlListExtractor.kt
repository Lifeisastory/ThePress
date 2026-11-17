package com.aster.core.network.adapters.extract

import com.aster.core.network.adapters.AdapterException
import com.aster.core.network.dto.HttpPayload
import com.aster.core.network.model.HtmlField
import com.aster.core.network.model.ProcessingStage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.select.Selector

/** DOM 只用于本地解析；不使用 Jsoup 发请求，不执行脚本 */
class HtmlListExtractor(
    private val itemSelector: String,
    private val fields: Map<String, HtmlField>
) : ContentExtractor {
    override fun extract(response: HttpPayload, json: Json): JsonArray {
        try {
            val rows = Jsoup.parse(response.body, response.url.toString()).select(itemSelector)
            if (rows.isEmpty()) throw AdapterException(ProcessingStage.EXTRACTION, "未找到 HTML 列表：$itemSelector")
            return JsonArray(rows.map { row ->
                buildJsonObject {
                    fields.forEach { (key, field) ->
                        val element = if (field.selector.isEmpty()) row else row.selectFirst(field.selector)
                        val value = element?.let { if (field.attribute == null) it.text() else it.attr(field.attribute) }
                        if (!value.isNullOrBlank()) put(key, value.trim())
                    }
                }
            })
        } catch (error: Selector.SelectorParseException) {
            throw AdapterException(ProcessingStage.EXTRACTION, "无效 HTML 选择器", error)
        }
    }
}
