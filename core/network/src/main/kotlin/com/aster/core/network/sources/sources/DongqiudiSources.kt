package com.aster.core.network.sources.sources

import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.sources.SourceDefaults
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object DongqiudiSources {
    val latest = SourceDefaults.json(
        "dongqiudi", "懂球帝", "https://api.dongqiudi.com/app/tabs/web/1.json", SourceDefaults.mapping()
    ).copy(transform = { documents ->
        JsonArray(JsonDocuments.merge(documents, "articles")
            .sortedByDescending { JsonDocuments.text(it, "created_at").orEmpty() }
            .map { item ->
                val id = JsonDocuments.requireText(item, "id")
                JsonObject(item + ("url" to JsonPrimitive(JsonDocuments.text(item, "share", "url") ?: "https://www.dongqiudi.com/article/$id")))
            })
    })
}
