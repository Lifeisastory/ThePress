package com.aster.core.network.sources.sources

import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.adapters.request.BatchRequests
import com.aster.core.network.sources.SourceDefaults
import kotlinx.serialization.json.JsonArray

internal object V2exSources {
    val share = SourceDefaults.json(
        "v2ex-share", "V2EX·分享", "https://www.v2ex.com/feed/create.json", SourceDefaults.mapping()
    ).copy(
        requestStrategy = BatchRequests(listOf("ideas", "programmer", "share").map {
            SourceDefaults.request("https://www.v2ex.com/feed/$it.json")
        }),
        transform = { documents ->
            JsonArray(JsonDocuments.merge(documents, "items").sortedByDescending {
                JsonDocuments.text(it, "date_modified", "date_published").orEmpty()
            })
        }
    )
}
