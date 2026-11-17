package com.aster.core.network.sources.sources

import com.aster.core.network.sources.SourceDefaults

internal object SolidotSources {
    val latest = SourceDefaults.feed("solidot", "Solidot", "https://www.solidot.org/index.rss")
}
