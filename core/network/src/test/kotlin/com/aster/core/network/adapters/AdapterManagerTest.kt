package com.aster.core.network.adapters

import com.aster.core.network.NetworkClient
import com.aster.core.network.adapters.extract.ContentExtractor
import com.aster.core.network.adapters.mapping.JsonPath
import com.aster.core.network.dto.SourceItem
import com.aster.core.network.model.HttpRequestSpec
import com.aster.core.network.model.ItemMapping
import com.aster.core.network.model.NetworkError
import com.aster.core.network.model.NetworkResult
import com.aster.core.network.model.ProcessingStage
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.model.UrlMapping
import com.aster.core.network.sources.BuiltInSources
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.EventListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdapterManagerTest {
    private lateinit var server: MockWebServer
    private lateinit var client: NetworkClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = NetworkClient()
    }

    @After
    fun tearDown() {
        client.httpClient.dispatcher.cancelAll()
        client.httpClient.connectionPool.evictAll()
        client.httpClient.dispatcher.executorService.shutdown()
        server.close()
    }

    private fun source() = SourceDefinition(
        id = "test",
        name = "测试源",
        request = HttpRequestSpec(server.url("/feed")),
        mapping = ItemMapping(JsonPath(""), JsonPath("id"), JsonPath("title"), UrlMapping.Field(JsonPath("url")))
    )

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/adapters/$name")).readText()

    @Test(timeout = 10_000)
    fun platformDefinitionsSelectDifferentToolsWithoutPlatformBranches() = runBlocking {
        val tencent = BuiltInSources.all.single { it.id == "qqvideo-tv-hotsearch" }.let {
            it.copy(request = it.request.copy(url = server.url("/tencent")))
        }
        val baidu = BuiltInSources.all.single { it.id == "baidu" }.let {
            it.copy(request = it.request.copy(url = server.url("/baidu"), query = it.request.query + ("q" to "中文 &x=1")))
        }
        val manager = AdapterManager(client, listOf(tencent, baidu))
        server.enqueue(MockResponse(body = fixture("tencent-video.json")))
        server.enqueue(MockResponse(body = fixture("baidu.html")))

        val videos = (manager.fetch(tencent.id) as NetworkResult.Success).data
        val news = (manager.fetch(baidu.id) as NetworkResult.Success).data

        assertEquals(2, videos.size)
        assertEquals(SourceItem(tencent.id, "test-cid-1", "测试剧集一", "https://v.qq.com/x/cover/test-cid-1.html", "剧集简介"), videos.first())
        assertEquals(listOf("测试新闻", "另一条新闻"), news.map { it.title })
        assertEquals(baidu.id, news.first().sourceId)
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("3000010", post.url.queryParameter("video_appid"))
        assertEquals("https://v.qq.com/", post.headers["Referer"])
        assertTrue(post.headers["Content-Type"]!!.startsWith("application/json"))
        val body = Json.parseToJsonElement(post.body!!.utf8())
        assertEquals(JsonPrimitive("热搜榜"), JsonPath("page_params.tab_name").read(body))
        assertEquals(JsonPrimitive("30"), JsonPath("page_params.rank_page_size").read(body))
        val get = server.takeRequest()
        assertEquals("GET", get.method)
        assertEquals("realtime", get.url.queryParameter("tab"))
        assertEquals("中文 &x=1", get.url.queryParameter("q"))
        assertNull(get.headers["Referer"])
    }

    @Test(timeout = 10_000)
    fun customExtractorCanBeRegisteredThroughARegularDefinition() = runBlocking {
        val custom = source().copy(extractor = ContentExtractor { response, _ ->
            buildJsonArray {
                add(buildJsonObject {
                    put("id", "1")
                    put("title", response.body)
                    put("url", "/1")
                })
            }
        })
        server.enqueue(MockResponse(body = "自定义格式标题"))
        val items = (AdapterManager(client, listOf(custom)).fetch("test") as NetworkResult.Success).data
        assertEquals("自定义格式标题", items.single().title)
    }

    @Test(timeout = 10_000)
    fun unknownIdsFailBeforeAnyNetworkRequest() = runBlocking {
        val result = AdapterManager(client, listOf(source())).fetch("missing") as NetworkResult.Failure
        assertEquals(ProcessingStage.SOURCE, (result.error as NetworkError.Processing).stage)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun duplicateIdsAreRejectedWhenRegistering() {
        assertThrows(IllegalArgumentException::class.java) { AdapterManager(client, listOf(source(), source())) }
    }

    @Test(timeout = 10_000)
    fun httpErrorsKeepTheExistingNetworkResultContract() = runBlocking {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "60").body("later").build())
        val result = AdapterManager(client, listOf(source())).fetch("test") as NetworkResult.Failure
        val error = result.error as NetworkError.Http
        assertEquals(429, error.code)
        assertEquals("later", error.body)
        assertEquals("60", error.headers["Retry-After"])
    }

    @Test(timeout = 10_000)
    fun extractionAndMappingFailuresReportTheirStage() = runBlocking {
        val manager = AdapterManager(client, listOf(source()))
        server.enqueue(MockResponse(body = "<html>validation</html>"))
        server.enqueue(MockResponse(body = """[{"id":"1","url":"/1"}]"""))
        val parse = (manager.fetch("test") as NetworkResult.Failure).error as NetworkError.Processing
        val mapping = (manager.fetch("test") as NetworkResult.Failure).error as NetworkError.Processing
        assertEquals(ProcessingStage.EXTRACTION, parse.stage)
        assertEquals(ProcessingStage.MAPPING, mapping.stage)
        assertEquals("test", mapping.sourceId)
    }

    @Test(timeout = 10_000)
    fun responseSizeLimitAppliesToKnownLengthAndChunkedBodies() = runBlocking {
        val limited = source().let { it.copy(request = it.request.copy(maxResponseBytes = 16)) }
        val manager = AdapterManager(client, listOf(limited))
        server.enqueue(MockResponse(body = "x".repeat(32)))
        server.enqueue(MockResponse.Builder().chunkedBody("x".repeat(32), 4).build())
        repeat(2) {
            val error = (manager.fetch("test") as NetworkResult.Failure).error as NetworkError.Processing
            assertEquals(ProcessingStage.REQUEST, error.stage)
        }
    }

    @Test(timeout = 10_000)
    fun relativeLinksUseTheFinalResponseUrlAfterRedirects() = runBlocking {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/redirect/final").build())
        server.enqueue(MockResponse(body = """[{"id":"1","title":"标题","url":"article"}]"""))
        val items = (AdapterManager(client, listOf(source())).fetch("test") as NetworkResult.Success).data
        assertEquals(server.url("/redirect/article").toString(), items.single().url)
    }

    @Test(timeout = 10_000)
    fun cancellationWhileReadingTheBodyCancelsTheHttpCall() = runBlocking {
        val bodyStarted = CountDownLatch(1)
        val bodyMayContinue = CountDownLatch(1)
        val canceled = CountDownLatch(1)
        val observedClient = client.httpClient.newBuilder().eventListener(object : EventListener() {
            override fun responseBodyStart(call: Call) {
                bodyStarted.countDown()
                check(bodyMayContinue.await(5, TimeUnit.SECONDS))
            }
            override fun canceled(call: Call) { canceled.countDown() }
        }).build()
        server.enqueue(MockResponse(body = "[]"))
        val manager = AdapterManager(NetworkClient(observedClient), listOf(source()))
        val job = async { manager.fetch("test") }
        try {
            withContext(Dispatchers.IO) { assertTrue(bodyStarted.await(5, TimeUnit.SECONDS)) }
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertTrue(canceled.await(5, TimeUnit.SECONDS))
        } finally {
            bodyMayContinue.countDown()
        }
    }
}
