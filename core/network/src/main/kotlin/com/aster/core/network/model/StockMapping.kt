package com.aster.core.network.model

import com.aster.core.network.adapters.mapping.JsonPath

data class StockMapping(
    val codePath: JsonPath,
    val pricePath: JsonPath,
    val changePath: JsonPath,
    val changePercentPath: JsonPath
)
