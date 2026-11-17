package com.aster.core.network.sources

internal object SspaiSources {
    val hot = SourceDefaults.json(
        "sspai", "少数派·热门文章",
        "https://sspai.com/api/v1/article/tag/page/get?limit=30&offset=0&tag=%E7%83%AD%E9%97%A8%E6%96%87%E7%AB%A0&released=false",
        SourceDefaults.mapping(items = "data", template = "https://sspai.com/post/{id}")
    ).copy(prepareRequest = { it.copy(query = it.query + ("created_at" to System.currentTimeMillis().toString())) })
}
