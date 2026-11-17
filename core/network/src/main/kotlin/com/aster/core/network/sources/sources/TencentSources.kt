package com.aster.core.network.sources.sources

import com.aster.core.network.sources.SourceDefaults

internal object TencentSources {
    val hot = SourceDefaults.json(
        "tencent-hot", "腾讯新闻·综合早报",
        "https://i.news.qq.com/web_backend/v2/getTagInfo?tagId=aEWqxLtdgmQ%3D",
        SourceDefaults.mapping(items = "data.tabs[0].articleList", url = "link_info.url", summary = "desc"),
        headers = mapOf("Referer" to "https://news.qq.com/")
    )
}
