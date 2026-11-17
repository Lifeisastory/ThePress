package com.aster.core.network.sources.sources

import com.aster.core.network.sources.SourceDefaults

internal object BilibiliSources {
    val hotSearch = SourceDefaults.json(
        "bilibili-hot-search", "哔哩哔哩·热搜",
        "https://s.search.bilibili.com/main/hotword?limit=30",
        SourceDefaults.mapping(items = "list", id = "keyword", title = "show_name", template = "https://search.bilibili.com/all?keyword={id}")
    )

    val hotVideo = SourceDefaults.json(
        "bilibili-hot-video", "哔哩哔哩·热门视频",
        "https://api.bilibili.com/x/web-interface/popular",
        SourceDefaults.mapping(items = "data.list", id = "bvid", summary = "desc", template = "https://www.bilibili.com/video/{id}")
    )

    val ranking = SourceDefaults.json(
        "bilibili-ranking", "哔哩哔哩·排行榜",
        "https://api.bilibili.com/x/web-interface/ranking/v2",
        SourceDefaults.mapping(items = "data.list", id = "bvid", summary = "desc", template = "https://www.bilibili.com/video/{id}")
    )
}
