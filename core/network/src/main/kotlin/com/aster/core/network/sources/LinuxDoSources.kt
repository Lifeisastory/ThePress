package com.aster.core.network.sources

import com.aster.core.network.adapters.mapping.JsonDocuments
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

internal object LinuxDoSources {
    private fun source(id: String, name: String, endpoint: String) = SourceDefaults.json(
        id, name, endpoint, SourceDefaults.mapping(template = "https://linux.do/t/topic/{id}")
    ).copy(transform = { documents ->
        JsonArray(JsonDocuments.merge(documents, "topic_list.topics").filter {
            it["visible"] == JsonPrimitive(true) && it["archived"] != JsonPrimitive(true) && it["pinned"] != JsonPrimitive(true)
        })
    })

    val latest = source("linuxdo-latest", "LINUX DO·最新", "https://linux.do/latest.json?order=created")
    val hot = source("linuxdo-hot", "LINUX DO·今日热门", "https://linux.do/top/daily.json")
}
