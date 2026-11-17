package com.aster.core.network.sources.sources

import com.aster.core.network.sources.SourceDefaults

internal object JuejinSources {
    val hot = SourceDefaults.json(
        "juejin", "掘金·文章榜",
        "https://api.juejin.cn/content_api/v1/content/article_rank?category_id=1&type=hot&spider=0",
        SourceDefaults.mapping(items = "data", id = "content.content_id", title = "content.title", template = "https://juejin.cn/post/{id}")
    )
}
