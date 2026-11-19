package com.aster.core.network.adapters

import com.aster.core.network.NetworkClient
import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.adapters.mapping.JsonPath
import com.aster.core.network.adapters.request.BatchRequests
import com.aster.core.network.adapters.request.CookieBootstrapRequest
import com.aster.core.network.adapters.request.FormChallengeRequest
import com.aster.core.network.adapters.request.RequestSigning
import com.aster.core.network.adapters.request.SourceRequestStrategy
import com.aster.core.network.model.HttpMethod
import com.aster.core.network.model.HttpRequestSpec
import com.aster.core.network.model.ItemMapping
import com.aster.core.model.NetworkError
import com.aster.core.model.NetworkResult
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.model.SourceCredentials
import com.aster.core.network.model.UrlMapping
import com.aster.core.network.sources.BuiltInSources
import java.net.InetAddress
import java.nio.charset.Charset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestStrategiesTest {
    private lateinit var server: MockWebServer
    private lateinit var client: NetworkClient
    private val body = """[{"id":"1","title":"标题","url":"/1"}]"""

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = NetworkClient()
    }

    @After fun tearDown() {
        client.httpClient.dispatcher.cancelAll()
        client.httpClient.connectionPool.evictAll()
        client.httpClient.dispatcher.executorService.shutdown()
        server.close()
    }

    private fun source(id: String = "test") = SourceDefinition(
        id, "测试", HttpRequestSpec(server.url("/list")),
        ItemMapping(JsonPath(""), JsonPath("id"), JsonPath("title"), UrlMapping.Field(JsonPath("url")), allowEmpty = false)
    )

    @Test(timeout = 10_000)
    fun bootstrapUsesOnlyCookiePairsAndEachFetchStartsANewSession() = runBlocking {
        val definition = source().copy(requestStrategy = CookieBootstrapRequest(HttpRequestSpec(server.url("/bootstrap"))))
        repeat(2) {
            server.enqueue(MockResponse.Builder().addHeader("Set-Cookie", "session=$it; Path=/; HttpOnly")
                .addHeader("Set-Cookie", "restricted=secret; Path=/private").body("ready").build())
            server.enqueue(MockResponse(body = body))
            assertTrue(AdapterManager(client, listOf(definition)).fetch("test") is NetworkResult.Success)
            assertNull(server.takeRequest().headers["Cookie"])
            assertEquals("session=$it", server.takeRequest().headers["Cookie"])
        }
        server.enqueue(MockResponse(body = body))
        AdapterManager(client, listOf(source())).fetch("test")
        assertNull(server.takeRequest().headers["Cookie"])
    }

    @Test(timeout = 10_000)
    fun domainCookiesCrossSubdomainsButPublicSuffixAndSecureCookiesDoNotLeak() = runBlocking {
        val localClient = NetworkClient(client.httpClient.newBuilder().dns { listOf(InetAddress.getByName("127.0.0.1")) }.build())
        val bootstrap = server.url("/bootstrap").newBuilder().host("login.example.com").build()
        val target = server.url("/list").newBuilder().host("api.example.com").build()
        val definition = source().copy(request = HttpRequestSpec(target), requestStrategy = CookieBootstrapRequest(HttpRequestSpec(bootstrap)))
        server.enqueue(MockResponse.Builder()
            .addHeader("Set-Cookie", "shared=yes; Domain=example.com; Path=/")
            .addHeader("Set-Cookie", "host=private; Path=/")
            .addHeader("Set-Cookie", "invalid=no; Domain=com; Path=/")
            .addHeader("Set-Cookie", "tls=secret; Domain=example.com; Path=/; Secure")
            .addHeader("Set-Cookie", "expired=old; Domain=example.com; Path=/; Max-Age=0")
            .body("ready").build())
        server.enqueue(MockResponse(body = body))
        assertTrue(AdapterManager(localClient, listOf(definition)).fetch("test") is NetworkResult.Success)
        server.takeRequest()
        assertEquals("shared=yes", server.takeRequest().headers["Cookie"])
    }

    @Test(timeout = 10_000)
    fun productHuntGraphqlErrorsFallBackToFeedWithoutForwardingAuthorization() = runBlocking {
        val configured = BuiltInSources.create(SourceCredentials(productHuntToken = "test-token")).first { it.id == "producthunt" }
        val feed = checkNotNull(configured.fallback).let { it.copy(request = it.request.copy(url = server.url("/feed"))) }
        val definition = configured.copy(request = configured.request.copy(url = server.url("/graphql")), fallback = feed)
        server.enqueue(MockResponse(body = """{"errors":[{"message":"unauthorized"}],"data":null}"""))
        server.enqueue(MockResponse(body = "<rss><channel><item><guid>1</guid><title>标题</title><link>/article</link></item></channel></rss>"))
        val item = (AdapterManager(client, listOf(definition)).fetch("producthunt") as NetworkResult.Success).data.single()
        assertEquals("producthunt", item.sourceId)
        assertEquals(server.url("/article").toString(), item.url)
        val graphql = server.takeRequest()
        assertEquals("POST", graphql.method)
        assertEquals("Bearer test-token", graphql.headers["Authorization"])
        assertTrue(graphql.body!!.utf8().contains("postedAfter"))
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test(timeout = 10_000)
    fun batchMergesAndDeduplicatesWhileAChildHttpFailureFailsTheWholeSource() = runBlocking {
        val definition = source().copy(
            requestStrategy = BatchRequests(listOf(HttpRequestSpec(server.url("/other")))),
            transform = { JsonArray(JsonDocuments.merge(it, "")) }
        )
        server.enqueue(MockResponse(body = body))
        server.enqueue(MockResponse(body = body))
        val manager = AdapterManager(client, listOf(definition))
        assertEquals(1, (manager.fetch("test") as NetworkResult.Success).data.size)
        server.enqueue(MockResponse(body = body))
        server.enqueue(MockResponse.Builder().code(503).body("down").build())
        assertTrue((manager.fetch("test") as NetworkResult.Failure).error is NetworkError.Http)
    }

    @Test(timeout = 10_000)
    fun fallbackPreservesPublicSourceIdAndRunsOnEmptyOrInvalidPrimaryData() = runBlocking {
        val fallback = source("fallback")
        val primary = source().copy(fallback = fallback)
        val manager = AdapterManager(client, listOf(primary))
        listOf("[]", "<html>challenge</html>").forEach { invalid ->
            server.enqueue(MockResponse(body = invalid))
            server.enqueue(MockResponse(body = body))
            assertEquals("test", (manager.fetch("test") as NetworkResult.Success).data.single().sourceId)
        }
    }

    @Test(timeout = 10_000)
    fun cancellationNeverInvokesFallback() = runBlocking {
        val primary = source().copy(
            requestStrategy = SourceRequestStrategy { _, _ -> throw CancellationException("canceled") }, fallback = source("fallback")
        )
        val job = async { AdapterManager(client, listOf(primary)).fetch("test") }
        try { job.await() } catch (_: CancellationException) { }
        assertTrue(job.isCancelled)
        assertEquals(0, server.requestCount)
    }

    @Test(timeout = 10_000)
    fun formReviewIsBoundedAndCookiesSurviveThePost() = runBlocking {
        val strategy = FormChallengeRequest(challenge = {
            if (it.body == "challenge") HttpRequestSpec(server.url("/review"), method = HttpMethod.POST, formBody = mapOf("value" to "中文 &x=1")) else null
        }, attempts = 1, delayMillis = 0)
        val definition = source().copy(requestStrategy = strategy, request = source().request.copy(acceptedStatusCodes = setOf(403)))
        server.enqueue(MockResponse.Builder().code(403).body("challenge").build())
        server.enqueue(MockResponse.Builder().addHeader("Set-Cookie", "verified=yes; Path=/").body("ok").build())
        server.enqueue(MockResponse(body = body))
        assertTrue(AdapterManager(client, listOf(definition)).fetch("test") is NetworkResult.Success)
        server.takeRequest()
        val form = server.takeRequest()
        assertEquals("POST", form.method)
        assertTrue(checkNotNull(form.headers["Content-Type"]).startsWith("application/x-www-form-urlencoded"))
        assertEquals("value=%E4%B8%AD%E6%96%87+%26x%3D1", form.body!!.utf8())
        assertEquals("verified=yes", server.takeRequest().headers["Cookie"])
        repeat(3) { server.enqueue(MockResponse(body = "challenge")) }
        val failed = AdapterManager(client, listOf(definition)).fetch("test")
        assertTrue(failed is NetworkResult.Failure)
        assertEquals(6, server.requestCount)
    }

    @Test(timeout = 10_000)
    fun explicitResponseCharsetSupportsLegacyPagesWithoutCharsetHeaders() = runBlocking {
        val charset = Charset.forName("GB18030")
        server.enqueue(MockResponse.Builder().body(Buffer().write(body.toByteArray(charset))).build())
        val definition = source().let { it.copy(request = it.request.copy(responseCharset = charset)) }
        val result = AdapterManager(client, listOf(definition)).fetch("test") as NetworkResult.Success
        assertEquals("标题", result.data.single().title)
    }

    @Test
    fun signingUsesUtf8LowercaseHexAndSortedFormEncoding() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", RequestSigning.digest("abc", "MD5"))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", RequestSigning.digest("abc", "SHA-1"))
        assertEquals("YWJj", RequestSigning.base64("abc"))
        assertEquals("a=hello+world&z=%E4%B8%AD%E6%96%87", RequestSigning.sortedFormQuery(mapOf("z" to "中文", "a" to "hello world")))
    }
}
