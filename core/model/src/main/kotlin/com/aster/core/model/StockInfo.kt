package com.aster.core.model

/** 行情快照 */
data class StockInfo(
    val code: String,
    val price: Double,
    val change: Double,
    val changePercent: Double
)
