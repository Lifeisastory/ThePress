package com.aster.core.network.dto

/** 归一化后的网络 DTO；身份由 sourceId 和 id 共同确定 */
data class SourceItem(
    val sourceId: String,
    val id: String,
    val title: String,
    val url: String,
    val summary: String? = null,
    val imageUrl: String? = null
)
