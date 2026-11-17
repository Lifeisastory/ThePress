package com.aster.core.network.sources.sources

import com.aster.core.network.sources.SourceDefaults

internal object AiHotSources {
    val latest = SourceDefaults.feed("aihot", "AI 热点", "https://aihot.news/feed/all.xml?aihot_actor=8d0ffb92-7f76-45e4-9d1b-8341f0eabf6c")
}
