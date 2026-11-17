package com.aster.core.network.sources.sources

import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.sources.SourceDefaults
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object ZhihuSources {
    val hot = SourceDefaults.json(
        "zhihu", "知乎·热榜", "https://www.zhihu.com/api/v3/feed/topstory/hot-list-web?limit=20&desktop=true",
        SourceDefaults.mapping(summary = "summary")
    ).copy(transform = { documents ->
        JsonArray(JsonDocuments.merge(documents, "data").map { item ->
            val url = JsonDocuments.requireText(item, "target.link.url")
            buildJsonObject {
                put("id", Regex("\\d+$").find(url)?.value ?: url)
                put("url", url)
                put("title", JsonDocuments.requireText(item, "target.title_area.text"))
                JsonDocuments.text(item, "target.excerpt_area.text")?.let { put("summary", it) }
            }
        })
    })
}
