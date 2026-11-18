package com.aster.core.data.repository

import com.aster.core.analytics.Logger
import com.aster.core.analytics.LogEvent
import com.aster.core.model.NewsFailureReason
import com.aster.core.model.NewsRefreshResult
import com.aster.core.network.NetworkClient
import com.aster.core.network.adapters.mapping.JsonPath
import com.aster.core.network.model.HttpRequestSpec
import com.aster.core.network.model.ItemMapping
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.model.UrlMapping
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import com.aster.core.localdb.entity.NewsCache
import com.aster.core.localdb.entity.NewsCacheEntity
import com.aster.core.localdb.entity.CachedNewsArticleEntity
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NewsRepositoryCacheTest {
    private lateinit var server: MockWebServer
    private lateinit var client: NetworkClient
    private lateinit var cache: FakeNewsCacheDao
    private lateinit var repository: NewsRepository
    private var now = 1_000_000L
    private val cacheErrors = mutableListOf<Exception>()
    private val events = mutableListOf<LogEvent>()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = NetworkClient()
        cache = FakeNewsCacheDao()
        repository = createRepository()
    }

    private fun createRepository() = OfflineFirstNewsRepository(
        client, cache,
        definitions = listOf("one", "two").map { id ->
            SourceDefinition(id, id, HttpRequestSpec(server.url("/$id")), ItemMapping(
                JsonPath(""), JsonPath("id"), JsonPath("title"), UrlMapping.Field(JsonPath("url")),
                imagePath = JsonPath("image")
            ))
        },
        currentTimeMillis = { now },
        logger = Logger { event ->
            events.add(event)
            if (event.name == "cache_failed") {
                assertEquals("cache", event.category)
                assertEquals("one", event.attributes["sourceId"])
                cacheErrors.add(event.cause as Exception)
            }
        }
    )

    @After
    fun tearDown() {
        client.httpClient.dispatcher.cancelAll()
        client.httpClient.connectionPool.evictAll()
        client.httpClient.dispatcher.executorService.shutdown()
        server.close()
    }

    private fun enqueue(id: String) {
        server.enqueue(MockResponse(body = """[{"id":"$id","title":"Title $id","url":"https://example.com/$id"}]"""))
    }

    @Test(timeout = 10_000)
    fun observationReceivesDatabaseChangesWithoutRefreshing() = runBlocking {
        val initial = CompletableDeferred<Unit>()
        val updates = async {
            withTimeout(5_000) {
                repository.observeNews().onEach { initial.complete(Unit) }.take(2).toList()
            }
        }
        initial.await()
        cache.replace(NewsCache(NewsCacheEntity("one", 0), listOf(
            CachedNewsArticleEntity("one", 0, "external", "Title", "https://example.com", null)
        )))
        val values = updates.await()
        assertTrue(values.first().isEmpty())
        assertEquals("external", values.last().single().id)
        assertEquals(0, server.requestCount)
        assertEquals(listOf("request_started", "request_response", "request_response", "request_finished"),
            events.map { it.name })
        assertEquals(1, events.map { it.attributes["requestId"] }.distinct().size)
        assertTrue(events.all { it.attributes["origin"] == "LOCAL_DATABASE" && it.attributes["operation"] == "OBSERVE" })
        assertEquals("all_sources", events.first().request!!.jsonObject.getValue("scope").jsonPrimitive.content)
        val response = events.filter { it.name == "request_response" }.last()
        assertEquals("2", response.attributes["sequence"])
        assertEquals("external", response.response!!.jsonObject.getValue("articles").jsonArray.single()
            .jsonObject.getValue("id").jsonPrimitive.content)
    }

    @Test(timeout = 10_000)
    fun failedWriteKeepsOldSnapshotAndRetryPublishesPersistedData() = runBlocking {
        enqueue("old")
        repository.refresh("one")
        now += 600_000
        cache.writeError = IllegalStateException("disk full")
        enqueue("unpersisted")
        assertEquals(NewsRefreshResult.Failure(NewsFailureReason.STORAGE), repository.refresh("one"))
        assertEquals("old", repository.observeNews().first().single().id)
        cache.writeError = null
        enqueue("new")
        assertEquals(NewsRefreshResult.Success, repository.refresh("one"))
        assertEquals("new", repository.observeNews().first().single().id)
    }

    @Test(timeout = 10_000)
    fun imagesSurviveCacheAndLocalSearchDoesNotFetchEvenAfterExpiry() = runBlocking {
        server.enqueue(MockResponse(body = """[{"id":"picture","title":"Title","url":"/article","image":"/cover.jpg"}]"""))
        assertEquals(NewsRefreshResult.Success, repository.refresh("one"))
        val articles = repository.observeNews().first()
        assertEquals(server.url("/cover.jpg").toString(), articles.single().imageUrl)
        assertEquals(NewsRefreshResult.Success, createRepository().refresh("one"))
        now += 600_000
        assertEquals(articles, repository.observeNews().first())
        assertEquals(articles, repository.search("  50%_\\  ", "one").first())
        assertEquals("%50\\%\\_\\\\%", cache.searchPattern)
        assertEquals("one", cache.searchSourceId)
        assertEquals(1, server.requestCount)
        val searchEvents = events.filter { it.attributes["operation"] == "SEARCH" }
        assertEquals(listOf("request_started", "request_response", "request_finished"), searchEvents.map { it.name })
        assertEquals("50%_\\", searchEvents.first().request!!.jsonObject.getValue("query").jsonPrimitive.content)
        assertEquals("one", searchEvents.first().request!!.jsonObject.getValue("sourceId").jsonPrimitive.content)
        assertEquals("picture", searchEvents[1].response!!.jsonObject.getValue("articles").jsonArray.single()
            .jsonObject.getValue("id").jsonPrimitive.content)
    }

    @Test(timeout = 10_000)
    fun successfulFetchIsPersistedAndReadByANewRepositoryUntilExpiry() = runBlocking {
        enqueue("first")
        val first = repository.refresh("one")
        assertEquals("first", cache.read("one")!!.articles.single().id)
        assertEquals(now, cache.read("one")!!.metadata.fetchedAt)

        now += 599_999
        assertEquals(first, createRepository().refresh("one"))
        assertEquals(1, server.requestCount)
        assertEquals(1_000_000L, cache.read("one")!!.metadata.fetchedAt)

        now++ // 刚好十分钟，必须请求网络并替换旧快照。
        enqueue("second")
        assertEquals(NewsRefreshResult.Success, repository.refresh("one"))
        assertEquals("second", repository.observeNews().first().single().id)
        assertEquals(listOf("second"), cache.read("one")!!.articles.map { it.id })
        assertEquals(now, cache.read("one")!!.metadata.fetchedAt)
        assertEquals(2, server.requestCount)
        assertTrue(events.any { it.attributes["outcome"] == "EXPIRED" })
    }

    @Test(timeout = 10_000)
    fun expiredCacheIsUnchangedOnNetworkFailureAndNextCallRetries() = runBlocking {
        enqueue("old")
        repository.refresh("one")
        val previous = cache.read("one")
        now += 600_000
        server.enqueue(MockResponse.Builder().code(503).build())
        assertEquals(NewsRefreshResult.Failure(NewsFailureReason.HTTP, 503), repository.refresh("one"))
        val failure = events.last { it.name == "request_finished" && it.attributes["origin"] == "NETWORK" }
        assertEquals("FAILED", failure.attributes["outcome"])
        assertEquals("HTTP", failure.response!!.jsonObject.getValue("reason").jsonPrimitive.content)
        assertEquals("503", failure.response!!.jsonObject.getValue("httpCode").jsonPrimitive.content)
        assertEquals(previous, cache.read("one"))

        enqueue("new")
        assertEquals(NewsRefreshResult.Success, repository.refresh("one"))
        assertEquals("new", repository.observeNews().first().single().id)
        assertEquals(3, server.requestCount)
    }

    @Test(timeout = 10_000)
    fun parsingFailureDoesNotCreateCache() = runBlocking {
        server.enqueue(MockResponse(body = "invalid json"))
        assertEquals(NewsRefreshResult.Failure(NewsFailureReason.INVALID_DATA), repository.refresh("one"))
        assertNull(cache.read("one"))
    }

    @Test(timeout = 10_000)
    fun sourcesHaveIndependentCacheAndExpiry() = runBlocking {
        enqueue("first")
        val first = repository.refresh("one")
        now += 300_000
        enqueue("second")
        val second = repository.refresh("two")
        assertEquals(first, repository.refresh("one"))
        assertEquals(second, repository.refresh("two"))
        now += 300_000
        enqueue("updated")
        repository.refresh("one")
        assertEquals(second, repository.refresh("two"))
        assertEquals(3, server.requestCount)
    }

    @Test(timeout = 10_000)
    fun successfulEmptyResultIsCachedAndReplacesPreviousArticles() = runBlocking {
        enqueue("old")
        repository.refresh("one")
        now += 600_000
        server.enqueue(MockResponse(body = "[]"))
        assertEquals(NewsRefreshResult.Success, repository.refresh("one"))
        assertEquals(emptyList<Any>(), cache.read("one")!!.articles)
        assertEquals(NewsRefreshResult.Success, createRepository().refresh("one"))
        assertEquals(2, server.requestCount)
        val response = events.last().response!!.jsonObject
        assertEquals("HIT", response.getValue("outcome").jsonPrimitive.content)
        assertEquals(0, response.getValue("articles").jsonArray.size)
    }

    @Test(timeout = 10_000)
    fun concurrentRequestsForTheSameSourceShareTheSuccessfulFetch() = runBlocking {
        enqueue("first")
        val results = List(8) { async { repository.refresh("one") } }.awaitAll()
        assertEquals(1, results.distinct().size)
        assertEquals(1, server.requestCount)
    }

    @Test(timeout = 10_000)
    fun clockMovingBackwardsInvalidatesCache() = runBlocking {
        enqueue("old")
        repository.refresh("one")
        now--
        enqueue("new")
        assertEquals(NewsRefreshResult.Success, repository.refresh("one"))
        assertEquals("new", repository.observeNews().first().single().id)
        assertEquals(2, server.requestCount)
    }

    @Test(timeout = 10_000)
    fun writeFailureReportsStorageFailureWithoutPublishingNetworkData() = runBlocking {
        cache.readError = IllegalStateException("read failure")
        cache.writeError = IllegalStateException("write failure")
        enqueue("first")
        assertEquals(NewsRefreshResult.Failure(NewsFailureReason.STORAGE), repository.refresh("one"))
        assertTrue(repository.observeNews().first().isEmpty())
        assertEquals(listOf(cache.readError, cache.writeError), cacheErrors)
        val failedOperations = events.filter {
            it.name == "request_finished" && it.attributes["origin"] == "LOCAL_DATABASE" &&
                it.attributes["operation"] in listOf("READ", "WRITE")
        }
        assertEquals(listOf("READ", "WRITE"), failedOperations.map { it.attributes["operation"] })
        assertTrue(failedOperations.all { it.attributes["outcome"] == "FAILED" })
    }

    @Test(timeout = 10_000)
    fun cacheCancellationPropagatesWithoutStartingNetworkRequest() = runBlocking {
        val cancellation = CancellationException("cancelled")
        cache.readError = cancellation
        try {
            repository.refresh("one")
            throw AssertionError("Expected cancellation")
        } catch (error: CancellationException) {
            assertEquals(cancellation, error)
        }
        assertEquals(0, server.requestCount)
        assertEquals(emptyList<Exception>(), cacheErrors)
        assertEquals(listOf("request_started", "request_finished"), events.map { it.name })
        assertEquals("CANCELED", events.last().attributes["outcome"])
        assertEquals("LOCAL_DATABASE", events.last().attributes["origin"])
    }

    @Test(timeout = 10_000)
    fun databaseAndNetworkRequestsLogPairedEventsAndStructuredArticles() = runBlocking {
        enqueue("first")
        repository.refresh("one")
        repository.refresh("one")
        assertEquals(1, server.requestCount)
        val requests = events.filter { it.category == "request" }.groupBy { it.attributes.getValue("requestId") }
        assertEquals(4, requests.size) // 数据库未命中、网络加载、缓存写入、数据库命中。
        requests.values.forEach { pair ->
            assertEquals(listOf("request_started", "request_finished"), pair.map { it.name })
            assertEquals(pair.first().attributes["origin"], pair.last().attributes["origin"])
            assertEquals("one", pair.first().attributes["sourceId"])
            assertTrue(pair.last().attributes.getValue("durationMs").toLong() >= 0)
            assertNotNull(pair.last().response)
            assertNotNull(pair.first().request)
        }
        val network = events.single { it.name == "request_finished" && it.attributes["origin"] == "NETWORK" }
        val local = events.last()
        assertEquals("LOCAL_DATABASE", local.attributes["origin"])
        assertEquals("HIT", local.attributes["outcome"])
        val articles = network.response!!.jsonObject.getValue("articles").jsonArray
        assertEquals(articles, local.response!!.jsonObject.getValue("articles"))
        assertEquals("first", articles.single().jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals("Title first", articles.single().jsonObject.getValue("title").jsonPrimitive.content)
        assertEquals("https://example.com/first", articles.single().jsonObject.getValue("url").jsonPrimitive.content)
        assertEquals("one", articles.single().jsonObject.getValue("sourceId").jsonPrimitive.content)
        assertEquals(JsonNull, articles.single().jsonObject.getValue("summary"))
    }

    @Test(timeout = 10_000)
    fun networkCancellationLogsItsCompletionWithoutWritingCache() = runBlocking {
        server.enqueue(MockResponse.Builder().body("[]").bodyDelay(5, TimeUnit.SECONDS).build())
        val request = async { repository.refresh("one") }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        request.cancelAndJoin()
        val network = events.filter { it.attributes["origin"] == "NETWORK" }
        assertEquals(listOf("request_started", "request_finished"), network.map { it.name })
        assertEquals("CANCELED", network.last().attributes["outcome"])
        assertNull(cache.read("one"))
    }
}
