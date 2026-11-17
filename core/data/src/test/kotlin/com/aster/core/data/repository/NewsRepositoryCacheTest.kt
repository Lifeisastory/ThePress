package com.aster.core.data.repository

import com.aster.core.data.model.NewsFailureReason
import com.aster.core.data.model.NewsLoadResult
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
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class NewsRepositoryCacheTest {
    private lateinit var server: MockWebServer
    private lateinit var client: NetworkClient
    private lateinit var cache: FakeNewsCacheDao
    private lateinit var repository: NewsRepository
    private var now = 1_000_000L
    private val cacheErrors = mutableListOf<Exception>()

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
                JsonPath(""), JsonPath("id"), JsonPath("title"), UrlMapping.Field(JsonPath("url"))
            ))
        },
        currentTimeMillis = { now },
        onCacheError = { _, error -> cacheErrors.add(error) }
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
    }

    @Test(timeout = 10_000)
    fun expiredCacheIsUnchangedOnNetworkFailureAndNextCallRetries() = runBlocking {
        enqueue("old")
        repository.fetch("one")
        val previous = cache.read("one")
        now += 600_000
        server.enqueue(MockResponse.Builder().code(503).build())
        assertEquals(NewsLoadResult.Failure(NewsFailureReason.HTTP, 503), repository.fetch("one"))
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
    }
}
