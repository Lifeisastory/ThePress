package com.aster.core.data.repository

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.LogLevel
import com.aster.core.analytics.Logger
import com.aster.core.analytics.NoOpLogger
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsFailureReason
import com.aster.core.model.NewsLoadResult
import com.aster.core.model.NewsSource
import com.aster.core.localdb.entity.CachedNewsArticleEntity
import com.aster.core.localdb.entity.NewsCache
import com.aster.core.localdb.dto.NewsCacheDao
import com.aster.core.localdb.entity.NewsCacheEntity
import com.aster.core.network.NetworkClient
import com.aster.core.network.logSourceFailure
import com.aster.core.network.adapters.AdapterManager
import com.aster.core.network.model.NetworkError
import com.aster.core.network.model.NetworkResult
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.sources.BuiltInSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 按来源优先读取十分钟内的本地快照，网络成功后整体替换缓存 */
class NewsRepository(
    client: NetworkClient,
    private val cacheDao: NewsCacheDao,
    definitions: List<SourceDefinition> = BuiltInSources.all,
    private val logger: Logger = NoOpLogger,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {
    private val adapters = AdapterManager(client, definitions)
    private val requestLogging = NewsRequestLogging(logger)
    private val sourceLocks = definitions.associate { it.id to Mutex() }
    val sources: List<NewsSource> = definitions.map { NewsSource(it.id, it.name) }

    /** 首屏和断网时也能读取过期快照；有效期只决定 fetch 是否需要联网 */
    suspend fun readCached(sourceId: String): List<NewsArticle>? =
        accessCache(sourceId) { cacheDao.read(sourceId)?.articles?.map { it.toArticle() } }

    fun search(query: String, sourceId: String?): Flow<List<NewsArticle>> {
        val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return cacheDao.search(sourceId, "%$escaped%").map { rows ->
            val sourceOrder = sources.mapIndexed { index, source -> source.id to index }.toMap()
            rows.filter { it.sourceId in sourceOrder }
                .sortedWith(compareBy<CachedNewsArticleEntity> { it.position }.thenBy { sourceOrder[it.sourceId] })
                .map { it.toArticle() }
        }
    }

    private fun CachedNewsArticleEntity.toArticle() =
        NewsArticle(sourceId, id, title, url, summary, imageUrl)

    suspend fun fetch(sourceId: String): NewsLoadResult {
        val lock = sourceLocks[sourceId] ?: return fetchFromNetwork(sourceId)
        // 同一来源的并发调用在锁内重新检查缓存，不同来源仍可并行请求
        return lock.withLock {
            val cached = accessCache(sourceId) {
                requestLogging.trace(sourceId, "LOCAL_DATABASE", "READ", describe = { (cache, fresh) ->
                    cache.asLogResponse(fresh)
                }) {
                    val cache = cacheDao.read(sourceId)
                    cache to (cache != null && currentTimeMillis() - cache.metadata.fetchedAt in 0 until CACHE_TTL_MILLIS)
                }
            }
            if (cached?.second == true) {
                val cache = checkNotNull(cached.first)
                NewsLoadResult.Success(cache.articles.map {
                    it.toArticle()
                })
            } else {
                fetchFromNetwork(sourceId)
            }
        }
    }

    private suspend fun fetchFromNetwork(sourceId: String): NewsLoadResult =
        requestLogging.trace(sourceId, "NETWORK", "FETCH", NewsLoadResult::asLogResponse) {
            loadFromNetwork(sourceId)
        }

    private suspend fun loadFromNetwork(sourceId: String): NewsLoadResult = when (val result = adapters.fetch(sourceId)) {
        is NetworkResult.Success -> {
            val articles = result.data.map { NewsArticle(it.sourceId, it.id, it.title, it.url, it.summary, it.imageUrl) }
            accessCache(sourceId) {
                requestLogging.trace(sourceId, "LOCAL_DATABASE", "WRITE", describe = { articles.asLogResponse() }) {
                    cacheDao.replace(NewsCache(
                        metadata = NewsCacheEntity(sourceId, currentTimeMillis()),
                        articles = articles.mapIndexed { position, article ->
                            CachedNewsArticleEntity(sourceId, position, article.id, article.title, article.url, article.summary, article.imageUrl)
                        }
                    ))
                }
            }
            NewsLoadResult.Success(articles)
        }
        is NetworkResult.Failure -> {
            logger.logSourceFailure(sourceId, result.error)
            when (val error = result.error) {
                is NetworkError.Connection -> NewsLoadResult.Failure(NewsFailureReason.CONNECTION)
                is NetworkError.Timeout -> NewsLoadResult.Failure(NewsFailureReason.TIMEOUT)
                is NetworkError.Http -> NewsLoadResult.Failure(NewsFailureReason.HTTP, error.code)
                is NetworkError.Serialization, is NetworkError.Processing ->
                    NewsLoadResult.Failure(NewsFailureReason.INVALID_DATA)
            }
        }
    }

    // 缓存不可用时仍允许网络加载；取消信号必须继续传递
    private suspend fun <T> accessCache(sourceId: String, block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        logger.log(LogEvent("cache_failed", "cache", LogLevel.WARN, mapOf("sourceId" to sourceId), error))
        null
    }

    private companion object {
        const val CACHE_TTL_MILLIS = 10 * 60 * 1000L
    }
}
