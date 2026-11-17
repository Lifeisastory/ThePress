package com.aster.core.network.sources

import com.aster.core.network.adapters.extract.EmbeddedJsonExtractor
import com.aster.core.network.adapters.mapping.JsonPath
import com.aster.core.network.model.HttpRequestSpec
import com.aster.core.network.model.ItemMapping
import com.aster.core.network.model.JsonFieldEquals
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.model.UrlMapping
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

internal object BaiduSources {
    val baidu = SourceDefinition(
        id = "baidu",
        name = "百度热搜",
        request = HttpRequestSpec(
            url = "https://top.baidu.com/board".toHttpUrl(),
            query = mapOf("tab" to "realtime"),
            headers = SourceDefaults.browserHeaders
        ),
        extractor = EmbeddedJsonExtractor("<!--s-data:", "-->"),
        mapping = ItemMapping(
            itemsPath = JsonPath("data.cards[0].content"),
            idPath = JsonPath("rawUrl"),
            titlePath = JsonPath("word"),
            url = UrlMapping.Field(JsonPath("rawUrl")),
            summaryPath = JsonPath("desc"),
            excludedItems = listOf(JsonFieldEquals(JsonPath("isTop"), JsonPrimitive(true))),
            allowEmpty = false
        )
    )

}
