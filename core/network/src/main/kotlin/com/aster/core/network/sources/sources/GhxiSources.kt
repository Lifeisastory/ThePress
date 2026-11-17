package com.aster.core.network.sources.sources

import com.aster.core.network.model.HtmlField
import com.aster.core.network.sources.SourceDefaults

internal object GhxiSources {
    val latest = SourceDefaults.html(
        "ghxi", "果核剥壳", "https://www.ghxi.com/category/all",
        ".article-item:has(.article-item__title a[href]), .sec-panel .sec-panel-body .post-loop li:has(.item-title a[href])",
        title = HtmlField(".article-item__title, .item-content .item-title"),
        link = HtmlField(".article-item__title a, .item-content .item-title a", "href"),
        summary = HtmlField(".article-item__excerpt, .item-content .item-excerpt")
    )
}
