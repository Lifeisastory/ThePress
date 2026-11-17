package com.aster.core.network.sources.sources

import com.aster.core.network.model.HtmlField
import com.aster.core.network.sources.SourceDefaults

internal object GelonghuiSources {
    val latest = SourceDefaults.html(
        "gelonghui", "格隆汇", "https://www.gelonghui.com/news/",
        ".article-content:has(.detail-right > a[href])",
        title = HtmlField(".detail-right > a h2"), link = HtmlField(".detail-right > a", "href")
    )
}
