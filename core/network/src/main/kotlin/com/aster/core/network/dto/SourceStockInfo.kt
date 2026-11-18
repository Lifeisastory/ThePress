package com.aster.core.network.dto

/** 网络行情快照 */
data class SourceStockInfo(
    val code: String,
    val price: Double,
    val change: Double,
    val changePercent: Double
)
