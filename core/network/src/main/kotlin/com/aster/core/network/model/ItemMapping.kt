package com.aster.core.network.model

import com.aster.core.network.adapters.mapping.JsonPath

data class ItemMapping(
    val itemsPath: JsonPath,
    val idPath: JsonPath,
    val titlePath: JsonPath,
    val url: UrlMapping,
    val summaryPath: JsonPath? = null,
    val requiredRootValues: List<JsonFieldEquals> = emptyList(),
    val excludedItems: List<JsonFieldEquals> = emptyList(),
    val allowEmpty: Boolean = true,
    val imagePath: JsonPath? = null,
    val stock: StockMapping? = null
)
