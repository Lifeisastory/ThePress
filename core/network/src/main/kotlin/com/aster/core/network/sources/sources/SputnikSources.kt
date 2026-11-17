package com.aster.core.network.sources.sources

import com.aster.core.network.model.HtmlField
import com.aster.core.network.sources.SourceDefaults

internal object SputnikSources {
    val latest = SourceDefaults.html(
        "sputniknewscn", "卫星通讯社", "https://sputniknews.cn/services/widget/lenta/",
        ".lenta__item:has(a[href])",
        title = HtmlField("a .lenta__item-text"), link = HtmlField("a", "href")
    )
}
