package com.aster.core.data.model

/** 应用使用的资讯条目，独立于网络 DTO 和数据库实体 */
data class NewsArticle(
    val sourceId: String,
    val id: String,
    val title: String,
    val url: String,
    val summary: String? = null
)
