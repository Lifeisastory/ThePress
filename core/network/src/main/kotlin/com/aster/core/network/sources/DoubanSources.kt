package com.aster.core.network.sources

internal object DoubanSources {
    val hot = SourceDefaults.json(
        "douban", "豆瓣·热门电影",
        "https://m.douban.com/rexxar/api/v2/subject/recent_hot/movie",
        SourceDefaults.mapping(items = "items", summary = "card_subtitle", template = "https://movie.douban.com/subject/{id}"),
        headers = mapOf("Referer" to "https://movie.douban.com/", "Accept" to "application/json, text/plain, */*")
    )
}
