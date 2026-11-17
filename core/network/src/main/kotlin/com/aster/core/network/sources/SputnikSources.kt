package com.aster.core.network.sources

import com.aster.core.network.model.HtmlField

internal object SputnikSources {
    val latest = SourceDefaults.html(
        "sputniknewscn", "卫星通讯社", "https://sputniknews.cn/services/widget/lenta/",
        ".lenta__item:has(a[href])",
        title = HtmlField("a .lenta__item-text"), link = HtmlField("a", "href")
    )
}
