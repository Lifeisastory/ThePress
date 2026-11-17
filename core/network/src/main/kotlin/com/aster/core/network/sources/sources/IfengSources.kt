package com.aster.core.network.sources.sources

import com.aster.core.network.adapters.extract.AssignedJsonExtractor
import com.aster.core.network.sources.SourceDefaults

internal object IfengSources {
    val hot = SourceDefaults.json(
        "ifeng", "凤凰网·热点", "https://www.ifeng.com/",
        SourceDefaults.mapping(items = "hotNews1", id = "url"),
        extractor = AssignedJsonExtractor("allData")
    )
}
