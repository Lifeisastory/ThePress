package com.aster.core.data.repository

import com.aster.core.localdb.entity.CachedNewsArticleEntity
import com.aster.core.localdb.entity.CachedStockInfo
import com.aster.core.model.NewsArticle
import com.aster.core.model.StockInfo
import com.aster.core.network.dto.SourceItem

internal fun SourceItem.toArticle() = NewsArticle(
    sourceId, id, title, url, summary, imageUrl,
    stockInfo?.let { StockInfo(it.code, it.price, it.change, it.changePercent) }
)

internal fun CachedNewsArticleEntity.toArticle() = NewsArticle(
    sourceId, id, title, url, summary, imageUrl,
    stockInfo?.let { StockInfo(it.code, it.price, it.change, it.changePercent) }
)

internal fun NewsArticle.toCacheEntity(position: Int) = CachedNewsArticleEntity(
    sourceId, position, id, title, url, summary, imageUrl,
    stockInfo?.let { CachedStockInfo(it.code, it.price, it.change, it.changePercent) }
)
