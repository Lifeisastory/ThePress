package com.aster.core.network.sources.sources

import com.aster.core.network.sources.SourceDefaults

internal object ThePaperSources {
    val hot = SourceDefaults.json(
        "thepaper", "澎湃新闻·热榜",
        "https://cache.thepaper.cn/contentapi/wwwIndex/rightSidebar",
        SourceDefaults.mapping(items = "data.hotNews", id = "contId", title = "name", template = "https://www.thepaper.cn/newsDetail_forward_{id}")
    )
}
