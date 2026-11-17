package com.aster.core.network

import com.aster.core.network.model.NetworkError
import com.aster.core.network.model.NetworkResult
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

class NetworkClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: NetworkClient
    private lateinit var api: ClientTestApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = NetworkClient()
        api = client.createService(server.url("/api/").toString())
    }

    @After
    fun tearDown() {
        client.httpClient.dispatcher.cancelAll()
        client.httpClient.connectionPool.evictAll()
        client.httpClient.dispatcher.executorService.shutdown()
        server.close()
    }

    @Test(timeout = 10_000)
    fun getDecodesJsonAndPreservesQueryAndHeader() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .body("""{"id":7,"title":"新闻","newField":true}""")
                .build()
        )

        val result = networkRequest { api.article("新闻 & 科技", "test-client") }

        assertEquals(NetworkResult.Success(ClientPayload(7, "新闻")), result)
        val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("GET", request.method)
        assertEquals("/api/article", request.url.encodedPath)
        assertEquals("新闻 & 科技", request.url.queryParameter("q"))
        assertEquals("test-client", request.headers["X-Client"])
    }

    @Test(timeout = 10_000)
    fun postEncodesJsonBody() = runBlocking {
        server.enqueue(MockResponse(body = """{"id":8,"title":"已提交"}"""))

        val result = networkRequest { api.submit(ClientPayload(8, "请求正文")) }

        assertEquals(NetworkResult.Success(ClientPayload(8, "已提交")), result)
        val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertTrue(request.headers["Content-Type"]!!.startsWith("application/json"))
        assertEquals(
            Json.parseToJsonElement("""{"id":8,"title":"请求正文"}"""),
            Json.parseToJsonElement(request.body!!.utf8())
        )
    }

    @Test(timeout = 10_000)
    fun stringResponsesKeepHtmlAndXmlUnchanged() = runBlocking {
        val html = "<html><body>新闻列表</body></html>"
        val xml = "<rss><channel><title>动态</title></channel></rss>"
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8")
                .body(html).build()
        )
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "application/rss+xml; charset=utf-8")
                .body(xml).build()
        )

        assertEquals(NetworkResult.Success(html), networkRequest { api.text() })
        assertEquals(NetworkResult.Success(xml), networkRequest { api.text() })
    }

    @Test(timeout = 10_000)
    fun textUsesCharsetDeclaredByServer() = runBlocking {
        val text = "中文页面"
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/html; charset=gb2312")
                .body(Buffer().write(text.toByteArray(Charset.forName("GB2312"))))
                .build()
        )

        assertEquals(NetworkResult.Success(text), networkRequest { api.text() })
    }

    @Test(timeout = 10_000)
    fun oneClientCanCreateServicesForDifferentBaseUrls() = runBlocking {
        MockWebServer().use { otherServer ->
            otherServer.start()
            server.enqueue(MockResponse(body = "first"))
            otherServer.enqueue(MockResponse(body = "second"))
            val otherApi = client.createService<ClientTestApi>(otherServer.url("/v2/").toString())

            assertEquals(NetworkResult.Success("first"), networkRequest { api.text() })
            assertEquals(NetworkResult.Success("second"), networkRequest { otherApi.text() })
            assertEquals("/api/text", server.takeRequest().url.encodedPath)
            assertEquals("/v2/text", otherServer.takeRequest().url.encodedPath)
        }
    }

    @Test(timeout = 10_000)
    fun customJsonConfigurationIsUsed() = runBlocking {
        val strictClient = NetworkClient(client.httpClient, Json { ignoreUnknownKeys = false })
        val strictApi = strictClient.createService<ClientTestApi>(server.url("/").toString())
        server.enqueue(MockResponse(body = """{"id":7,"title":"新闻","unexpected":true}"""))

        val result = networkRequest { strictApi.article("test", "client") }

        assertTrue((result as NetworkResult.Failure).error is NetworkError.Serialization)
    }

    @Test(timeout = 10_000)
    fun unitSupportsNoContentResponses() = runBlocking {
        server.enqueue(MockResponse(code = 204))

        assertEquals(NetworkResult.Success(Unit), networkRequest { api.delete() })
    }

    @Test(timeout = 10_000)
    fun successfulResponseKeepsHttpMetadata() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(201)
                .addHeader("X-Request-Id", "request-1")
                .body("""{"id":7,"title":"新闻"}""")
                .build()
        )

        val result = networkRequest { api.response() } as NetworkResult.Success

        assertEquals(201, result.data.code())
        assertEquals("request-1", result.data.headers()["X-Request-Id"])
        assertEquals(ClientPayload(7, "新闻"), result.data.body())
    }
}

@Serializable
internal data class ClientPayload(val id: Int, val title: String)

internal interface ClientTestApi {
    @GET("article")
    suspend fun article(@Query("q") query: String, @Header("X-Client") client: String): ClientPayload

    @POST("article")
    suspend fun submit(@Body payload: ClientPayload): ClientPayload

    @GET("text")
    suspend fun text(): String

    @DELETE("article")
    suspend fun delete()

    @GET("article")
    suspend fun response(): Response<ClientPayload>
}
