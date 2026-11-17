package com.aster.core.network.sources.sources

import com.aster.core.network.model.HtmlField
import com.aster.core.network.sources.SourceDefaults

internal object ChongbuluoSources {
    val hot = SourceDefaults.html(
        "chongbuluo-hot", "虫部落·热门", "https://www.chongbuluo.com/forum.php?mod=guide&view=hot",
        ".bmw table tr:has(.common .xst[href])",
        title = HtmlField(".common .xst"), link = HtmlField(".common .xst", "href")
    )
    val latest = SourceDefaults.feed(
        "chongbuluo-latest", "虫部落·最新", "https://www.chongbuluo.com/forum.php?mod=rss&view=newthread"
    )
}
