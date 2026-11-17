package com.aster.core.network.sources.sources

import com.aster.core.network.adapters.extract.HtmlListExtractor
import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.model.HtmlField
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.sources.SourceDefaults
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object FastbullSources {
    val express = SourceDefinition(
        "fastbull-express", "FastBull·快讯", SourceDefaults.request("https://www.fastbull.com/cn/express-news"),
        SourceDefaults.mapping(summary = "summary"),
        HtmlListExtractor(".content-list.news-list", mapOf(
            "title" to HtmlField(".title_name"), "href" to HtmlField("[data-href]", "data-href"),
            "key" to HtmlField("[data-id]", "data-id"), "date" to HtmlField(attribute = "data-date")
        )),
        transform = { documents ->
            JsonArray(JsonDocuments.merge(documents, "").filter { JsonDocuments.text(it, "href", "key") != null }
                .sortedByDescending { JsonDocuments.text(it, "date")?.toLongOrNull() ?: 0 }.map {
                    val key = JsonDocuments.requireText(it, "href", "key")
                    val text = JsonDocuments.requireText(it, "title")
                    val headline = Regex("【(.+)】").find(text)?.groupValues?.get(1)?.takeIf { title -> title.length >= 4 }
                    buildJsonObject {
                        put("id", key)
                        put("title", headline ?: text)
                        put("summary", text)
                        put("url", if (key.startsWith("/") || key.startsWith("https://")) key else "/cn/fastshort/$key")
                    }
                })
        }
    )
    val news = SourceDefaults.html(
        "fastbull-news", "FastBull·头条", "https://www.fastbull.com/cn/news", ".trending_type[href]",
        title = HtmlField(".title"), link = HtmlField(attribute = "href")
    )
}
