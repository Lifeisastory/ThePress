package com.aster.core.network.sources.sources

import com.aster.core.network.model.HtmlField
import com.aster.core.network.sources.SourceDefaults

internal object GithubSources {
    val trending = SourceDefaults.html(
        "github-trending-today", "GitHub·今日趋势", "https://github.com/trending?spoken_language_code=",
        "main .Box div[data-hpc] > article:has(h2 a[href])",
        title = HtmlField("h2 a"), link = HtmlField("h2 a", "href"), summary = HtmlField("p")
    )
}
