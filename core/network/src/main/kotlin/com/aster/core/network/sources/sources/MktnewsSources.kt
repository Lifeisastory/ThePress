package com.aster.core.network.sources.sources

import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.sources.SourceDefaults
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object MktnewsSources {
    val flash = SourceDefaults.json(
        "mktnews-flash", "MKTNews·快讯", "https://api.mktnews.net/api/flash?type=0&limit=50",
        SourceDefaults.mapping(template = "https://mktnews.net/flashDetail.html?id={id}", summary = "summary"),
        headers = mapOf("Origin" to "https://mktnews.net", "Referer" to "https://mktnews.net/")
    ).copy(transform = { documents ->
        JsonArray(JsonDocuments.merge(documents, "data").sortedByDescending { JsonDocuments.text(it, "time").orEmpty() }.map {
            val content = JsonDocuments.text(it, "data.content").orEmpty()
            buildJsonObject {
                put("id", JsonDocuments.requireText(it, "id"))
                put("title", JsonDocuments.text(it, "data.title") ?: Regex("^【([^】]+)】").find(content)?.groupValues?.get(1) ?: content)
                put("summary", content)
            }
        })
    })
}
