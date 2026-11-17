package com.aster.core.network.sources.sources

import com.aster.core.network.model.HtmlField
import com.aster.core.network.sources.SourceDefaults

internal object SmzdmSources {
    val hot = SourceDefaults.html(
        "smzdm", "什么值得买·热门", "https://post.smzdm.com/hot_1/",
        "#feed-main-list .z-feed-title:has(a[href]:not(.feed-title-pre-tag))",
        title = HtmlField("a:not(.feed-title-pre-tag)"), link = HtmlField("a:not(.feed-title-pre-tag)", "href")
    )
}
