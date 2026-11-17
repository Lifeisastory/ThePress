package com.aster.core.network.sources.sources

import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.model.HtmlField
import com.aster.core.network.sources.SourceDefaults
import kotlinx.serialization.json.JsonArray

internal object IthomeSources {
    val latest = SourceDefaults.html(
        "ithome", "IT之家", "https://www.ithome.com/list/", "#list > div.fl > ul > li:has(a.t[href])",
        title = HtmlField("a.t"), link = HtmlField("a.t", "href")
    ).copy(transform = { documents ->
        JsonArray(JsonDocuments.merge(documents, "").filter { item ->
            val title = JsonDocuments.requireText(item, "title")
            !JsonDocuments.requireText(item, "url").contains("lapin") &&
                listOf("神券", "优惠", "补贴", "京东").none { it in title }
        })
    })
}
