package com.aster.core.network.sources

internal object ToutiaoSources {
    val hot = SourceDefaults.json(
        "toutiao", "今日头条·热榜",
        "https://www.toutiao.com/hot-event/hot-board/?origin=toutiao_pc",
        SourceDefaults.mapping(items = "data", id = "ClusterIdStr", title = "Title", template = "https://www.toutiao.com/trending/{id}/")
    )
}
