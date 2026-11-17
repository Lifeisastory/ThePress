package com.aster.core.network.sources

import com.aster.core.network.adapters.extract.ContentExtractor
import com.aster.core.network.adapters.extract.FeedExtractor
import com.aster.core.network.adapters.extract.HtmlListExtractor
import com.aster.core.network.adapters.extract.JsonExtractor
import com.aster.core.network.adapters.mapping.JsonPath
import com.aster.core.network.model.HtmlField
import com.aster.core.network.model.HttpRequestSpec
import com.aster.core.network.model.ItemMapping
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.model.UrlMapping
import okhttp3.HttpUrl.Companion.toHttpUrl

/** 平台定义的公共默认值；请求与解析实现在 adapters 中 */
internal object SourceDefaults {
    val browserHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    )

    fun request(url: String, headers: Map<String, String> = emptyMap()) =
        HttpRequestSpec(url.toHttpUrl(), headers = browserHeaders + headers)

    fun mapping(
        items: String = "", id: String = "id", title: String = "title",
        url: String = "url", template: String? = null, summary: String? = null,
        image: String? = null
    ) = ItemMapping(
        JsonPath(items), JsonPath(id), JsonPath(title),
        template?.let { UrlMapping.Template(it) } ?: UrlMapping.Field(JsonPath(url)),
        summary?.let { JsonPath(it) }, allowEmpty = false, imagePath = image?.let { JsonPath(it) }
    )

    fun json(
        id: String, name: String, endpoint: String, mapping: ItemMapping,
        headers: Map<String, String> = emptyMap(), extractor: ContentExtractor = JsonExtractor
    ) = SourceDefinition(id, name, request(endpoint, headers), mapping, extractor)

    fun html(
        id: String, name: String, endpoint: String, rows: String,
        title: HtmlField, link: HtmlField, summary: HtmlField? = null,
        itemId: HtmlField = link, image: HtmlField? = null
    ): SourceDefinition = SourceDefinition(
        id, name, request(endpoint), mapping(summary = summary?.let { "summary" }, image = image?.let { "image" }),
        HtmlListExtractor(rows, buildMap {
            put("id", itemId)
            put("title", title)
            put("url", link)
            if (summary != null) put("summary", summary)
            if (image != null) put("image", image)
        })
    )

    fun feed(id: String, name: String, endpoint: String) =
        SourceDefinition(id, name, request(endpoint), mapping(summary = "summary", image = "image"), FeedExtractor)
}
