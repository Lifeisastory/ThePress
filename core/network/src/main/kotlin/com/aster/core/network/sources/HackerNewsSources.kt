package com.aster.core.network.sources

import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.model.HtmlField
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object HackerNewsSources {
    private val feed = SourceDefaults.feed("hackernews", "Hacker News", "https://hnrss.org/frontpage?count=30").copy(
        transform = { documents ->
            JsonArray(JsonDocuments.merge(documents, "").map { item ->
                val id = JsonDocuments.requireText(item, "id")
                // hnrss 的 guid 为讨论页；保持与直接抓取的条目 ID 和链接一致。
                val discussionId = Regex("[?&]id=(\\d+)").find(id)?.groupValues?.get(1)
                if (discussionId == null) item else JsonObject(item + mapOf(
                    "id" to JsonPrimitive(discussionId), "url" to JsonPrimitive("https://news.ycombinator.com/item?id=$discussionId")
                ))
            })
        }
    )
    val hot = SourceDefaults.html(
        "hackernews", "Hacker News", "https://news.ycombinator.com/", ".athing:has(.titleline a)",
        title = HtmlField(".titleline a"), link = HtmlField(".titleline a", "href"), itemId = HtmlField(attribute = "id")
    ).copy(mapping = SourceDefaults.mapping(template = "https://news.ycombinator.com/item?id={id}"), fallback = feed)
}
