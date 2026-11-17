package com.aster.core.network.sources.sources

import com.aster.core.network.adapters.extract.AssignedJsonExtractor
import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.sources.SourceDefaults
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl

internal object KuaishouSources {
    val hot = SourceDefaults.json(
        "kuaishou", "快手·热榜", "https://www.kuaishou.com/?isHome=1", SourceDefaults.mapping(),
        extractor = AssignedJsonExtractor("window.__APOLLO_STATE__")
    ).copy(transform = { documents ->
        val client = (documents.single() as? JsonObject)?.get("defaultClient") as? JsonObject ?: JsonDocuments.fail("缺少 Apollo 缓存")
        val query = client["ROOT_QUERY"] as? JsonObject ?: JsonDocuments.fail("缺少 ROOT_QUERY")
        val ref = query["visionHotRank({\"page\":\"home\"})"] ?: JsonDocuments.fail("缺少热榜引用")
        val rank = client[JsonDocuments.requireText(ref, "id")] ?: JsonDocuments.fail("热榜引用不存在")
        JsonArray(JsonDocuments.array(rank, "items").mapNotNull { reference ->
            val key = JsonDocuments.requireText(reference, "id")
            val item = client[key] ?: JsonDocuments.fail("热榜条目引用不存在：$key")
            if (JsonDocuments.text(item, "tagType") == "置顶") null else {
                val title = JsonDocuments.requireText(item, "name")
                buildJsonObject {
                    put("id", key.removePrefix("VisionHotRankItem:"))
                    put("title", title)
                    put("url", "https://www.kuaishou.com/search/video".toHttpUrl().newBuilder().addQueryParameter("searchKey", title).build().toString())
                }
            }
        })
    })
}
