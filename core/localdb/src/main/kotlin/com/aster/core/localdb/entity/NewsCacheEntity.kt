package com.aster.core.localdb.entity

import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** 独立记录成功时间，使空列表也能命中缓存 */
@Entity(tableName = "news_cache")
data class NewsCacheEntity(
    @PrimaryKey val sourceId: String,
    val fetchedAt: Long
)

@Entity(
    tableName = "cached_news_articles",
    primaryKeys = ["sourceId", "position"],
    foreignKeys = [ForeignKey(
        entity = NewsCacheEntity::class,
        parentColumns = ["sourceId"],
        childColumns = ["sourceId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class CachedNewsArticleEntity(
    val sourceId: String,
    val position: Int,
    val id: String,
    val title: String,
    val url: String,
    val summary: String?,
    val imageUrl: String? = null,
    @Embedded(prefix = "stock_") val stockInfo: CachedStockInfo? = null
)

data class CachedStockInfo(
    val code: String,
    val price: Double,
    val change: Double,
    val changePercent: Double
)

data class NewsCache(
    val metadata: NewsCacheEntity,
    val articles: List<CachedNewsArticleEntity>
)
