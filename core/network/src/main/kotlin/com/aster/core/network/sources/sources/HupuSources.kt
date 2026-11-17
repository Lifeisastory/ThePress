package com.aster.core.network.sources.sources

import com.aster.core.network.model.HtmlField
import com.aster.core.network.sources.SourceDefaults

internal object HupuSources {
    val hot = SourceDefaults.html(
        "hupu", "虎扑·热榜", "https://bbs.hupu.com/topic-daily-hot",
        "li.bbs-sl-web-post-body:has(a.p-title[href])",
        title = HtmlField("a.p-title"), link = HtmlField("a.p-title", "href")
    )
}
