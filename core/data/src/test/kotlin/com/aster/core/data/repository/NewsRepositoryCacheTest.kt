package com.aster.core.data.repository

import com.aster.core.analytics.Logger
import com.aster.core.analytics.LogEvent
import com.aster.core.model.NewsFailureReason
import com.aster.core.model.NewsLoadResult
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

    private fun createRepository() = NewsRepository(
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
    fun imagesSurviveCacheAndLocalSearchDoesNotFetchEvenAfterExpiry() = runBlocking {
        server.enqueue(MockResponse(body = """[{"id":"picture","title":"Title","url":"/article","image":"/cover.jpg"}]"""))
        val result = repository.fetch("one") as NewsLoadResult.Success
        assertEquals(server.url("/cover.jpg").toString(), result.articles.single().imageUrl)
        assertEquals(result, createRepository().fetch("one"))
        now += 600_000
        assertEquals(result.articles, repository.readCached("one"))
        assertEquals(result.articles, repository.search("  50%_\\  ", "one").first())
        assertEquals("%50\\%\\_\\\\%", cache.searchPattern)
        assertEquals("one", cache.searchSourceId)
        assertEquals(1, server.requestCount)
    }

    @Test(timeout = 10_000)
    fun successfulFetchIsPersistedAndReadByANewRepositoryUntilExpiry() = runBlocking {
        enqueue("first")
        val first = repository.fetch("one") as NewsLoadResult.Success
        assertEquals("first", cache.read("one")!!.articles.single().id)
        assertEquals(now, cache.read("one")!!.metadata.fetchedAt)

        now += 599_999
        assertEquals(first, createRepository().fetch("one"))
        assertEquals(1, server.requestCount)
        assertEquals(1_000_000L, cache.read("one")!!.metadata.fetchedAt)

        now++ // 刚好十分钟，必须请求网络并替换旧快照。
        enqueue("second")
        val second = repository.fetch("one") as NewsLoadResult.Success
        assertEquals("second", second.articles.single().id)
        assertEquals(listOf("second"), cache.read("one")!!.articles.map { it.id })
        assertEquals(now, cache.read("one")!!.metadata.fetchedAt)
        assertEquals(2, server.requestCount)
        assertTrue(events.any { it.attributes["outcome"] == "EXPIRED" })
    }

    @Test(timeout = 10_000)
    fun expiredCacheIsUnchangedOnNetworkFailureAndNextCallRetries() = runBlocking {
        enqueue("old")
        repository.fetch("one")
        val previous = cache.read("one")
        now += 600_000
        server.enqueue(MockResponse.Builder().code(503).build())
        assertEquals(NewsLoadResult.Failure(NewsFailureReason.HTTP, 503), repository.fetch("one"))
        val failure = events.last { it.name == "request_finished" && it.attributes["origin"] == "NETWORK" }
        assertEquals("FAILED", failure.attributes["outcome"])
        assertEquals("HTTP", failure.response!!.jsonObject.getValue("reason").jsonPrimitive.content)
        assertEquals("503", failure.response!!.jsonObject.getValue("httpCode").jsonPrimitive.content)
        assertEquals(previous, cache.read("one"))

        enqueue("new")
        assertEquals("new", (repository.fetch("one") as NewsLoadResult.Success).articles.single().id)
        assertEquals(3, server.requestCount)
    }

    @Test(timeout = 10_000)
    fun parsingFailureDoesNotCreateCache() = runBlocking {
        server.enqueue(MockResponse(body = "invalid json"))
        assertEquals(NewsLoadResult.Failure(NewsFailureReason.INVALID_DATA), repository.fetch("one"))
        assertNull(cache.read("one"))
    }

    @Test(timeout = 10_000)
    fun sourcesHaveIndependentCacheAndExpiry() = runBlocking {
        enqueue("first")
        val first = repository.fetch("one")
        now += 300_000
        enqueue("second")
        val second = repository.fetch("two")
        assertEquals(first, repository.fetch("one"))
        assertEquals(second, repository.fetch("two"))
        now += 300_000
        enqueue("updated")
        repository.fetch("one")
        assertEquals(second, repository.fetch("two"))
        assertEquals(3, server.requestCount)
    }

    @Test(timeout = 10_000)
    fun successfulEmptyResultIsCachedAndReplacesPreviousArticles() = runBlocking {
        enqueue("old")
        repository.fetch("one")
        now += 600_000
        server.enqueue(MockResponse(body = "[]"))
        assertEquals(NewsLoadResult.Success(emptyList()), repository.fetch("one"))
        assertEquals(emptyList<Any>(), cache.read("one")!!.articles)
        assertEquals(NewsLoadResult.Success(emptyList()), createRepository().fetch("one"))
        assertEquals(2, server.requestCount)
        val response = events.last().response!!.jsonObject
        assertEquals("HIT", response.getValue("outcome").jsonPrimitive.content)
        assertEquals(0, response.getValue("articles").jsonArray.size)
    }

    @Test(timeout = 10_000)
    fun concurrentRequestsForTheSameSourceShareTheSuccessfulFetch() = runBlocking {
        enqueue("first")
        val results = List(8) { async { repository.fetch("one") } }.awaitAll()
        assertEquals(1, results.distinct().size)
        assertEquals(1, server.requestCount)
    }

    @Test(timeout = 10_000)
    fun clockMovingBackwardsInvalidatesCache() = runBlocking {
        enqueue("old")
        repository.fetch("one")
        now--
        enqueue("new")
        assertEquals("new", (repository.fetch("one") as NewsLoadResult.Success).articles.single().id)
        assertEquals(2, server.requestCount)
    }

    @Test(timeout = 10_000)
    fun cacheReadAndWriteFailuresStillReturnSuccessfulNetworkData() = runBlocking {
        cache.readError = IllegalStateException("read failure")
        cache.writeError = IllegalStateException("write failure")
        enqueue("first")
        assertEquals("first", (repository.fetch("one") as NewsLoadResult.Success).articles.single().id)
        assertEquals(listOf(cache.readError, cache.writeError), cacheErrors)
        val failedOperations = events.filter {
            it.name == "request_finished" && it.attributes["origin"] == "LOCAL_DATABASE"
        }
        assertEquals(listOf("READ", "WRITE"), failedOperations.map { it.attributes["operation"] })
        assertTrue(failedOperations.all { it.attributes["outcome"] == "FAILED" })
    }

    @Test(timeout = 10_000)
    fun cacheCancellationPropagatesWithoutStartingNetworkRequest() = runBlocking {
        val cancellation = CancellationException("cancelled")
        cache.readError = cancellation
        try {
            repository.fetch("one")
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
        repository.fetch("one")
        repository.fetch("one")
        assertEquals(1, server.requestCount)
        val requests = events.filter { it.category == "request" }.groupBy { it.attributes.getValue("requestId") }
        assertEquals(4, requests.size) // 数据库未命中、网络加载、缓存写入、数据库命中。
        requests.values.forEach { pair ->
            assertEquals(listOf("request_started", "request_finished"), pair.map { it.name })
            assertEquals(pair.first().attributes["origin"], pair.last().attributes["origin"])
            assertEquals("one", pair.first().attributes["sourceId"])
            assertTrue(pair.last().attributes.getValue("durationMs").toLong() >= 0)
            assertNotNull(pair.last().response)
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
        val request = async { repository.fetch("one") }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        request.cancelAndJoin()
        val network = events.filter { it.attributes["origin"] == "NETWORK" }
        assertEquals(listOf("request_started", "request_finished"), network.map { it.name })
        assertEquals("CANCELED", network.last().attributes["outcome"])
        assertNull(cache.read("one"))
    }
}
