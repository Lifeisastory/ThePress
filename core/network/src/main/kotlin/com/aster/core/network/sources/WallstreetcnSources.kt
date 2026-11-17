package com.aster.core.network.sources

import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.adapters.mapping.JsonPath
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object WallstreetcnSources {
    val quick = SourceDefaults.json(
        "wallstreetcn-quick", "华尔街见闻·快讯",
        "https://api-one.wallstcn.com/apiv1/content/lives?channel=global-channel&limit=30",
        SourceDefaults.mapping(url = "uri")
    ).copy(transform = { documents ->
        JsonArray(JsonDocuments.merge(documents, "data.items").map {
            JsonObject(it + ("title" to JsonPrimitive(JsonDocuments.requireText(it, "title", "content_text"))))
        })
    })
    val news = SourceDefaults.json(
        "wallstreetcn-news", "华尔街见闻·资讯",
        "https://api-one.wallstcn.com/apiv1/content/information-flow?channel=global-channel&accept=article&limit=30",
        SourceDefaults.mapping(url = "uri", summary = "content_short")
    ).copy(transform = { documents ->
        JsonArray(JsonDocuments.merge(documents, "data.items")
            .filter { JsonDocuments.text(it, "resource_type") !in listOf("theme", "ad") }
            .map { JsonPath("resource").read(it) as? JsonObject ?: JsonDocuments.fail("缺少 resource 对象") }
            .filter { JsonDocuments.text(it, "type") != "live" && JsonDocuments.text(it, "uri") != null }
            .map { JsonObject(it + ("title" to JsonPrimitive(JsonDocuments.requireText(it, "title", "content_short")))) })
    })
    val hot = SourceDefaults.json(
        "wallstreetcn-hot", "华尔街见闻·热门", "https://api-one.wallstcn.com/apiv1/content/articles/hot?period=all",
        SourceDefaults.mapping(items = "data.day_items", url = "uri")
    )
}
