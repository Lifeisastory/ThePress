package com.aster.core.data.repository

import com.aster.core.agent.*
import com.aster.core.analytics.Logger
import com.aster.core.localdb.dto.NewsDigestDao
import com.aster.core.localdb.entity.NewsDigestEntity
import com.aster.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

internal class DefaultNewsDigestRepository(
    private val news: NewsRepository,
    private val settings: DigestSettingsRepository,
    private val apiKey: Flow<String>,
    private val dao: NewsDigestDao,
    client: LlmClient,
    logger: Logger,
    private val clock: () -> Long = System::currentTimeMillis
) : NewsDigestRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val logs = DataRequestLogging(logger)
    private val generator = DigestGenerator(client, logger)
    private val lock = Mutex()

    override fun observeLatestDigest(): Flow<NewsDigest?> = logs.observe(
        origin = "LOCAL_DATABASE", resource = "news_digest", request = { buildJsonObject {} },
        describe = { digest -> buildJsonObject {
            put("outcome", if (digest == null) "MISS" else "SUCCESS")
            put("digest", digest?.let { json.encodeToJsonElement(NewsDigest.serializer(), it) } ?: JsonNull)
        } }
    ) {
        dao.observeLatest().map { it?.let { json.decodeFromString(NewsDigest.serializer(), it.payload) } }
    }.flowOn(Dispatchers.Default)

    override suspend fun generateDigest(): DigestGenerationResult = withContext(Dispatchers.Default) {
        if (!lock.tryLock()) return@withContext DigestGenerationResult.Failure(DigestFailure.BUSY)
        try {
            val config = settings.settings.first()
            val key = logs.trace(origin = "LOCAL_PREFERENCES", operation = "READ", resource = "llm_credentials",
                describe = { buildJsonObject { put("outcome", "SUCCESS"); put("configured", it.isNotBlank()) } }
            ) { apiKey.first() }
            if (key.isBlank()) throw DigestGenerationException(DigestFailure.NOT_CONFIGURED)
            val sources = news.sources.filter { it.id in config.sourceIds }
            if (sources.isEmpty()) throw DigestGenerationException(DigestFailure.NO_SOURCES)
            val semaphore = Semaphore(4)
            val refreshes = coroutineScope {
                sources.map { source -> async { semaphore.withPermit { source.id to news.refresh(source.id) } } }.awaitAll().toMap()
            }
            val articles = news.observeNews().first().filter { article -> sources.any { it.id == article.sourceId } }
                .distinctBy { it.sourceId to it.id }
            if (articles.isEmpty()) throw DigestGenerationException(DigestFailure.NO_NEWS)
            val names = sources.associate { it.id to it.name }
            val references = articles.mapIndexed { index, article -> DigestReference(
                "n${index + 1}", article.sourceId, names.getValue(article.sourceId), article.id,
                article.title, article.summary, article.url
            ) }
            val items = generator.generate(references, config, key)
            val digest = NewsDigest(clock(), config.model, config.detail, sources.map { source ->
                DigestSourceStatus(source.id, source.name, articles.count { it.sourceId == source.id },
                    refreshes[source.id] is NewsRefreshResult.Failure)
            }, references.size, items)
            currentCoroutineContext().ensureActive()
            logs.trace(origin = "LOCAL_DATABASE", operation = "WRITE", resource = "news_digest",
                request = { buildJsonObject { put("digest", json.encodeToJsonElement(NewsDigest.serializer(), digest)) } },
                describe = { buildJsonObject { put("outcome", "SUCCESS") } }) {
                dao.save(NewsDigestEntity(payload = json.encodeToString(NewsDigest.serializer(), digest)))
            }
            DigestGenerationResult.Success
        } catch (error: CancellationException) {
            throw error
        } catch (error: DigestGenerationException) {
            DigestGenerationResult.Failure(error.reason)
        } catch (error: LlmException) {
            DigestGenerationResult.Failure(when (error.reason) {
                LlmFailure.CONNECTION -> DigestFailure.CONNECTION
                LlmFailure.TIMEOUT -> DigestFailure.TIMEOUT
                LlmFailure.INVALID_RESPONSE, LlmFailure.TRUNCATED -> DigestFailure.INVALID_RESPONSE
                LlmFailure.HTTP -> when (error.httpCode) {
                    401, 403 -> DigestFailure.AUTHENTICATION
                    402 -> DigestFailure.BALANCE
                    429 -> DigestFailure.RATE_LIMIT
                    else -> DigestFailure.SERVER
                }
            })
        } catch (_: Exception) {
            DigestGenerationResult.Failure(DigestFailure.STORAGE)
        } finally { lock.unlock() }
    }
}
