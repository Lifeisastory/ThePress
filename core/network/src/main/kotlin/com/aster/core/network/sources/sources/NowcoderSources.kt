package com.aster.core.network.sources.sources

import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.sources.SourceDefaults
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object NowcoderSources {
    val hot = SourceDefaults.json(
        "nowcoder", "牛客·热榜", "https://gw-c.nowcoder.com/api/sparta/hot-search/top-hot-pc?size=20&t=", SourceDefaults.mapping()
    ).copy(
        prepareRequest = { it.copy(query = it.query + ("_" to System.currentTimeMillis().toString())) },
        transform = { documents ->
            JsonArray(JsonDocuments.merge(documents, "data.result").filter {
                JsonDocuments.text(it, "type") in listOf("0", "74")
            }.map {
                val feed = JsonDocuments.text(it, "type") == "74"
                val id = JsonDocuments.requireText(it, if (feed) "uuid" else "id")
                JsonObject(it + mapOf("id" to JsonPrimitive(id), "url" to JsonPrimitive(
                    if (feed) "https://www.nowcoder.com/feed/main/detail/$id" else "https://www.nowcoder.com/discuss/$id"
                )))
            })
        }
    )
}
