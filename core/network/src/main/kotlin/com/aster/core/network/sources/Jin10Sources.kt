package com.aster.core.network.sources

import com.aster.core.network.adapters.extract.AssignedJsonExtractor
import com.aster.core.network.adapters.mapping.JsonDocuments
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object Jin10Sources {
    val latest = SourceDefaults.json(
        "jin10", "金十数据", "https://www.jin10.com/flash_newest.js",
        SourceDefaults.mapping(template = "https://flash.jin10.com/detail/{id}", summary = "summary"),
        extractor = AssignedJsonExtractor("newest")
    ).copy(
        prepareRequest = { it.copy(query = it.query + ("t" to System.currentTimeMillis().toString())) },
        transform = { documents ->
            JsonArray(JsonDocuments.merge(documents, "").filter {
                JsonDocuments.text(it, "data.title", "data.content") != null &&
                    (it["channel"] as? JsonArray)?.contains(JsonPrimitive(5)) != true
            }.map { item ->
                val text = JsonDocuments.plainText(JsonDocuments.requireText(item, "data.title", "data.content"))
                val headline = Regex("^【([^】]+)】(.*)$", RegexOption.DOT_MATCHES_ALL).find(text)
                buildJsonObject {
                    put("id", JsonDocuments.requireText(item, "id"))
                    put("title", headline?.groupValues?.get(1) ?: text)
                    headline?.groupValues?.get(2)?.let { put("summary", it) }
                }
            })
        }
    )
}
