package com.aster.core.data.repository

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.LogLevel
import com.aster.core.analytics.LogOutcome
import com.aster.core.analytics.LogResponse
import com.aster.core.analytics.Logger
import com.aster.core.analytics.NoOpLogger
import com.aster.core.analytics.RequestLogging
import com.aster.core.analytics.log
import com.aster.core.data.asLogResponse
import com.aster.core.data.logSourceFailure
import com.aster.core.data.model.toArticle
import com.aster.core.data.model.toCacheEntity
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsFailureReason
import com.aster.core.model.NewsRefreshResult
import com.aster.core.model.NewsSource
import com.aster.core.model.NewsCacheStats
import com.aster.core.localdb.entity.CachedNewsArticleEntity
import com.aster.core.localdb.entity.NewsCache
import com.aster.core.localdb.dao.NewsCacheDao
import com.aster.core.localdb.entity.NewsCacheEntity
import com.aster.core.network.NetworkClient
import com.aster.core.network.adapters.AdapterManager
import com.aster.core.network.dto.SourceItem
import com.aster.core.model.NetworkError
import com.aster.core.model.NetworkResult
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.sources.BuiltInSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 新闻只从数据库读取；刷新负责检查有效期并将网络结果持久化 */
internal class OfflineFirstNewsRepository(
    client: NetworkClient,
    private val cacheDao: NewsCacheDao,
    definitions: List<SourceDefinition> = BuiltInSources.all,
    private val logger: Logger = NoOpLogger,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : NewsRepository {
    private val adapters = AdapterManager(client, definitions)
    private val requestLogging = RequestLogging(logger)
    private val sourceLocks = definitions.associate { it.id to Mutex() }
    override val sources: List<NewsSource> = definitions.map { NewsSource(it.id, it.name) }

    override fun observeCacheStats(): Flow<NewsCacheStats> = cacheDao.observeCacheStats()
        .map { NewsCacheStats(it.articleCount, it.contentBytes) }.distinctUntilChanged()

    override suspend fun clearCache() {
        // 与所有来源的刷新互斥，避免正在进行的请求在清理返回后写回旧快照
        val locked = mutableListOf<Mutex>()
        try {
            sourceLocks.values.forEach { lock -> lock.lock(); locked.add(lock) }
            requestLogging.trace(
                origin = "LOCAL_DATABASE", operation = "DELETE", resource = "news",
                request = { buildJsonObject { put("scope", "all_news_cache") } },
                describe = { LogResponse(LogOutcome.SUCCESS) }
            ) { cacheDao.clearCache() }
        } finally {
            locked.asReversed().forEach { it.unlock() }
        }
    }

    override fun observeNews(): Flow<List<NewsArticle>> = requestLogging.observe(
        origin = "LOCAL_DATABASE", resource = "news",
        request = { buildJsonObject { put("scope", "all_sources") } },
        describe = { it.asLogResponse() }
    ) {
        cacheDao.observeArticles().map { rows ->
            rows.filter { it.sourceId in sourceLocks }.map { it.toArticle() }
        }.distinctUntilChanged()
    }

    override fun search(query: String, sourceId: String?): Flow<List<NewsArticle>> {
        val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return requestLogging.observe(
            sourceId = sourceId,
            origin = "LOCAL_DATABASE",
            operation = "SEARCH",
            resource = "news",
            request = { buildJsonObject {
                put("query", query.trim())
                put("sourceId", sourceId?.let(::JsonPrimitive) ?: JsonNull)
            } },
            describe = { it.asLogResponse() }
        ) {
            cacheDao.search(sourceId, "%$escaped%").map { rows ->
                val sourceOrder = sources.mapIndexed { index, source -> source.id to index }.toMap()
                rows.filter { it.sourceId in sourceOrder }
                    .sortedWith(compareBy<CachedNewsArticleEntity> { it.position }.thenBy { sourceOrder[it.sourceId] })
                    .map { it.toArticle() }
            }
        }
    }

    override suspend fun refresh(sourceId: String): NewsRefreshResult {
        val lock = sourceLocks[sourceId] ?: return NewsRefreshResult.Failure(NewsFailureReason.INVALID_DATA)
        // 同一来源的并发调用在锁内重新检查缓存，不同来源仍可并行请求
        return lock.withLock {
            val cached = accessCache(sourceId) {
                requestLogging.trace(sourceId, "LOCAL_DATABASE", "READ", resource = "news", describe = { (cache, fresh) ->
                    cache.asLogResponse(fresh)
                }) {
                    val cache = cacheDao.read(sourceId)
                    cache to (cache != null && currentTimeMillis() - cache.metadata.fetchedAt in 0 until CACHE_TTL_MILLIS)
                }
            }
            if (cached?.second == true) {
                NewsRefreshResult.Success
            } else {
                refreshFromNetwork(sourceId)
            }
        }
    }

    private suspend fun refreshFromNetwork(sourceId: String): NewsRefreshResult {
        val result = requestLogging.trace(sourceId, "NETWORK", "FETCH", resource = "news", describe = ::describeNetworkResult) {
            adapters.fetch(sourceId)
        }
        return when (result) {
            is NetworkResult.Success -> {
                val articles = result.data.map { it.toArticle() }
                val persisted = accessCache(sourceId) {
                    requestLogging.trace(sourceId, "LOCAL_DATABASE", "WRITE", resource = "news", request = {
                        buildJsonObject {
                            put("sourceId", sourceId)
                            put("count", articles.size)
                            put("articles", articles.asLogResponse().payload.getValue("articles"))
                        }
                    }, describe = { articles.asLogResponse() }) {
                        cacheDao.replace(NewsCache(
                            metadata = NewsCacheEntity(sourceId, currentTimeMillis()),
                            articles = articles.mapIndexed { position, article ->
                                article.toCacheEntity(position)
                            }
                        ))
                    }
                    true
                }
                if (persisted == true) NewsRefreshResult.Success
                else NewsRefreshResult.Failure(NewsFailureReason.STORAGE)
            }
            is NetworkResult.Failure -> {
                logger.logSourceFailure(sourceId, result.error)
                result.error.toRefreshFailure()
            }
        }
    }

    private fun describeNetworkResult(result: NetworkResult<List<SourceItem>>): LogResponse = when (result) {
        is NetworkResult.Success -> result.data.map { it.toArticle() }.asLogResponse()
        is NetworkResult.Failure -> result.error.toRefreshFailure().asLogResponse()
    }

    private fun NetworkError.toRefreshFailure(): NewsRefreshResult.Failure = when (this) {
        is NetworkError.Connection -> NewsRefreshResult.Failure(NewsFailureReason.CONNECTION)
        is NetworkError.Timeout -> NewsRefreshResult.Failure(NewsFailureReason.TIMEOUT)
        is NetworkError.Http -> NewsRefreshResult.Failure(NewsFailureReason.HTTP, code)
        is NetworkError.Serialization, is NetworkError.Processing ->
            NewsRefreshResult.Failure(NewsFailureReason.INVALID_DATA)
    }

    // 缓存不可用时仍允许网络加载；取消信号必须继续传递
    private suspend fun <T> accessCache(sourceId: String, block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        logger.log { LogEvent("cache_failed", "cache", LogLevel.WARN, mapOf("sourceId" to sourceId), error) }
        null
    }

    private companion object {
        const val CACHE_TTL_MILLIS = 10 * 60 * 1000L
    }
}
