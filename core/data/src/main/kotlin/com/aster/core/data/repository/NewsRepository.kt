package com.aster.core.data.repository

import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsRefreshResult
import com.aster.core.model.NewsSource
import kotlinx.coroutines.flow.Flow

interface NewsRepository {
    val sources: List<NewsSource>

    /** 持续观察本地新闻，包含过期快照；不会触发网络请求 */
    fun observeNews(): Flow<List<NewsArticle>>

    fun search(query: String, sourceId: String?): Flow<List<NewsArticle>>

    /** 遵循十分钟有效期；成功表示已有有效快照或新内容已写入数据库 */
    suspend fun refresh(sourceId: String): NewsRefreshResult
}
