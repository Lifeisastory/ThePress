package com.aster.core.data.repository

import com.aster.core.localdb.entity.CachedNewsArticleEntity
import com.aster.core.localdb.entity.NewsCache
import com.aster.core.localdb.dto.NewsCacheDao
import com.aster.core.localdb.entity.NewsCacheEntity

internal class FakeNewsCacheDao : NewsCacheDao() {
    private val metadata = mutableMapOf<String, NewsCacheEntity>()
    private val articles = mutableMapOf<String, List<CachedNewsArticleEntity>>()
    var readError: Exception? = null
    var writeError: Exception? = null

    override suspend fun read(sourceId: String): NewsCache? {
        readError?.let { throw it }
        return super.read(sourceId)
    }

    override suspend fun replace(cache: NewsCache) {
        writeError?.let { throw it }
        super.replace(cache)
    }

    override suspend fun metadata(sourceId: String) = metadata[sourceId]
    override suspend fun articles(sourceId: String) = articles[sourceId].orEmpty().sortedBy { it.position }
    override suspend fun upsertMetadata(metadata: NewsCacheEntity) { this.metadata[metadata.sourceId] = metadata }
    override suspend fun deleteArticles(sourceId: String) { articles.remove(sourceId) }
    override suspend fun insertArticles(articles: List<CachedNewsArticleEntity>) {
        articles.groupBy { it.sourceId }.forEach { (sourceId, items) -> this.articles[sourceId] = items }
    }
}
