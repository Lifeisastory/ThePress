package com.aster.core.data.repository

import com.aster.core.localdb.entity.CachedNewsArticleEntity
import com.aster.core.localdb.entity.NewsCache
import com.aster.core.localdb.dto.NewsCacheDao
import com.aster.core.localdb.dto.NewsCacheSize
import com.aster.core.localdb.entity.NewsCacheEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeNewsCacheDao : NewsCacheDao() {
    private val metadata = mutableMapOf<String, NewsCacheEntity>()
    private val articles = mutableMapOf<String, List<CachedNewsArticleEntity>>()
    var readError: Exception? = null
    var writeError: Exception? = null
    private val changes = MutableStateFlow(0)
    var searchPattern: String? = null
    var searchSourceId: String? = null

    override fun observeCacheStats() = observeArticles().map { rows ->
        NewsCacheSize(rows.size.toLong(), rows.sumOf { row ->
            listOfNotNull(row.sourceId, row.id, row.title, row.url, row.summary, row.imageUrl, row.stockInfo?.code)
                .sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
        })
    }

    override suspend fun clearCache() {
        writeError?.let { throw it }
        metadata.clear()
        articles.clear()
        changes.value++
    }

    override fun observeArticles() = changes.map {
        articles.values.flatten().sortedWith(compareBy<CachedNewsArticleEntity> { it.sourceId }.thenBy { it.position })
    }

    override fun search(sourceId: String?, pattern: String) = changes.map {
        searchPattern = pattern
        searchSourceId = sourceId
        // SQL 匹配由真实 Room 测试验证；这里用于验证仓库参数、映射及更新
        articles.values.flatten().filter { sourceId == null || it.sourceId == sourceId }
    }

    override suspend fun read(sourceId: String): NewsCache? {
        readError?.let { throw it }
        return super.read(sourceId)
    }

    override suspend fun replace(cache: NewsCache) {
        writeError?.let { throw it }
        super.replace(cache)
        changes.value++
    }

    override suspend fun metadata(sourceId: String) = metadata[sourceId]
    override suspend fun articles(sourceId: String) = articles[sourceId].orEmpty().sortedBy { it.position }
    override suspend fun upsertMetadata(metadata: NewsCacheEntity) { this.metadata[metadata.sourceId] = metadata }
    override suspend fun deleteArticles(sourceId: String) { articles.remove(sourceId) }
    override suspend fun insertArticles(articles: List<CachedNewsArticleEntity>) {
        articles.groupBy { it.sourceId }.forEach { (sourceId, items) -> this.articles[sourceId] = items }
    }
}
