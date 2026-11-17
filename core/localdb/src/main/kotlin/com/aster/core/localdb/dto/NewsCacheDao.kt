package com.aster.core.localdb.dto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.aster.core.localdb.entity.CachedNewsArticleEntity
import com.aster.core.localdb.entity.NewsCache
import com.aster.core.localdb.entity.NewsCacheEntity

@Dao
abstract class NewsCacheDao {
    @Query("SELECT * FROM news_cache WHERE sourceId = :sourceId")
    protected abstract suspend fun metadata(sourceId: String): NewsCacheEntity?

    @Query("SELECT * FROM cached_news_articles WHERE sourceId = :sourceId ORDER BY position")
    protected abstract suspend fun articles(sourceId: String): List<CachedNewsArticleEntity>

    @Upsert
    protected abstract suspend fun upsertMetadata(metadata: NewsCacheEntity)

    @Query("DELETE FROM cached_news_articles WHERE sourceId = :sourceId")
    protected abstract suspend fun deleteArticles(sourceId: String)

    @Insert
    protected abstract suspend fun insertArticles(articles: List<CachedNewsArticleEntity>)

    @Transaction
    open suspend fun read(sourceId: String): NewsCache? {
        val metadata = metadata(sourceId) ?: return null
        return NewsCache(metadata, articles(sourceId))
    }

    /** 时间戳和完整列表一起提交，读取方不会看到新时间配旧数据 */
    @Transaction
    open suspend fun replace(cache: NewsCache) {
        require(cache.articles.all { it.sourceId == cache.metadata.sourceId })
        upsertMetadata(cache.metadata)
        deleteArticles(cache.metadata.sourceId)
        insertArticles(cache.articles)
    }
}
