package com.aster.core.data.repository

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.LogLevel
import com.aster.core.analytics.Logger
import com.aster.core.data.model.NewsArticle
import com.aster.core.data.model.NewsFailureReason
import com.aster.core.data.model.NewsLoadResult
import com.aster.core.network.NetworkClient
import com.aster.core.network.sources.BuiltInSources
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NewsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var client: NetworkClient
    private lateinit var repository: NewsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = NetworkClient()
        repository = NewsRepository(client, FakeNewsCacheDao(), BuiltInSources.all.map {
            it.copy(request = it.request.copy(url = server.url("/${it.id}")))
        })
    }

    @After
    fun tearDown() {
        client.httpClient.dispatcher.cancelAll()
        client.httpClient.connectionPool.evictAll()
        client.httpClient.dispatcher.executorService.shutdown()
        server.close()
    }

    @Test(timeout = 10_000)
    fun bothBuiltInSourcesReachTheApplicationModelThroughTheRealAdapters() = runBlocking {
        server.enqueue(MockResponse(body = """
            {"ret":0,"data":{"card":{"children_list":{"list":{"cards":[
                {"id":"video-1","params":{"title":"测试剧集","sub_title":"剧集简介"}}
            ]}}}}}
        """.trimIndent()))
        server.enqueue(MockResponse(body = """
            <html><!--s-data:{"data":{"cards":[{"content":[
                {"word":"测试新闻","rawUrl":"https://example.com/news/1","desc":"新闻摘要"}
            ]}]}}--></html>
        """.trimIndent()))

        assertEquals(BuiltInSources.all.map { it.id }, repository.sources.map { it.id })
        assertEquals(
            NewsLoadResult.Success(listOf(NewsArticle(
                "qqvideo-tv-hotsearch", "video-1", "测试剧集", "https://v.qq.com/x/cover/video-1.html", "剧集简介"
            ))),
            repository.fetch("qqvideo-tv-hotsearch")
        )
        assertEquals(
            NewsLoadResult.Success(listOf(NewsArticle(
                "baidu", "https://example.com/news/1", "测试新闻", "https://example.com/news/1", "新闻摘要"
            ))),
            repository.fetch("baidu")
        )
    }

    @Test(timeout = 10_000)
    fun httpAndParsingFailuresBecomeDataErrorsWithoutExposingRawResponses() = runBlocking {
        server.enqueue(MockResponse.Builder().code(503).body("unavailable").build())
        server.enqueue(MockResponse(body = "<html>validation required</html>"))

        assertEquals(NewsLoadResult.Failure(NewsFailureReason.HTTP, 503), repository.fetch("baidu"))
        assertEquals(NewsLoadResult.Failure(NewsFailureReason.INVALID_DATA), repository.fetch("baidu"))
    }

    @Test(timeout = 10_000)
    fun diagnosticsKeepTheSourceIdAndOriginalDnsFailure() = runBlocking {
        val cause = UnknownHostException("synthetic DNS failure")
        val failingClient = NetworkClient(client.httpClient.newBuilder().dns { throw cause }.build())
        val source = BuiltInSources.all.single { it.id == "baidu" }.let {
            it.copy(request = it.request.copy(url = "https://dns.test.invalid/".toHttpUrl()))
        }
        val errors = mutableListOf<LogEvent>()
        val repository = NewsRepository(failingClient, FakeNewsCacheDao(), listOf(source), logger = Logger {
            if (it.name == "source_failed") errors.add(it)
        })

        assertEquals(NewsLoadResult.Failure(NewsFailureReason.CONNECTION), repository.fetch("baidu"))
        assertEquals("source_failed", errors.single().name)
        assertEquals(LogLevel.ERROR, errors.single().level)
        assertEquals("baidu", errors.single().attributes["sourceId"])
        val reportedCause = checkNotNull(errors.single().cause)
        assertEquals(cause.javaClass, reportedCause.javaClass)
        assertEquals(cause.message, reportedCause.message)
    }
}
