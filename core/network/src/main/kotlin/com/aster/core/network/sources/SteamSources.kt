package com.aster.core.network.sources

import com.aster.core.network.model.HtmlField

internal object SteamSources {
    val hot = SourceDefaults.html(
        "steam", "Steam·在线人数榜", "https://store.steampowered.com/stats/stats/",
        "#detailStats tr.player_count_row:has(a.gameLink[href])",
        title = HtmlField("a.gameLink"), link = HtmlField("a.gameLink", "href")
    )
}
