package com.aster.core.network

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.EventListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkRequestTest {
    private lateinit var server: MockWebServer
    private lateinit var client: NetworkClient
    private lateinit var api: ClientTestApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = NetworkClient()
        api = client.createService(server.url("/").toString())
    }

    @After
    fun tearDown() {
        client.httpClient.dispatcher.cancelAll()
        client.httpClient.connectionPool.evictAll()
        client.httpClient.dispatcher.executorService.shutdown()
        server.close()
    }

    @Test(timeout = 10_000)
    fun httpFailureKeepsStatusHeadersAndErrorBody() = runBlocking {
        val body = """{"message":"稍后重试"}"""
        server.enqueue(
            MockResponse.Builder().code(429).addHeader("Retry-After", "60").body(body).build()
        )

        val result = networkRequest { api.article("test", "client") }
        val error = (result as NetworkResult.Failure).error as NetworkError.Http

        assertEquals(429, error.code)
        assertEquals("60", error.headers["Retry-After"])
        assertEquals(body, error.body)
        assertEquals(1, server.requestCount)
    }

    @Test(timeout = 10_000)
    fun responseReturnTypeAlsoChecksHttpStatus() = runBlocking {
        server.enqueue(MockResponse(code = 503, body = "unavailable"))

        val result = networkRequest { api.response() }
        val error = (result as NetworkResult.Failure).error as NetworkError.Http

        assertEquals(503, error.code)
        assertEquals("unavailable", error.body)
    }

    @Test(timeout = 10_000)
    fun malformedJsonIsSerializationFailure() = runBlocking {
        server.enqueue(MockResponse(body = "<html>Unexpected page</html>"))

        val result = networkRequest { api.article("test", "client") }

        assertTrue((result as NetworkResult.Failure).error is NetworkError.Serialization)
    }

    @Test(timeout = 10_000)
    fun missingRequiredJsonFieldIsSerializationFailure() = runBlocking {
        server.enqueue(MockResponse(body = """{"id":7}"""))

        val result = networkRequest { api.article("test", "client") }

        assertTrue((result as NetworkResult.Failure).error is NetworkError.Serialization)
    }

    @Test(timeout = 10_000)
    fun ioFailureKeepsExceptionTypeAndMessage() = runBlocking {
        val cause = IOException("connection unavailable")
        val failingClient = client.httpClient.newBuilder()
            .addInterceptor { throw cause }
            .build()
        val failingApi = NetworkClient(failingClient)
            .createService<ClientTestApi>(server.url("/").toString())

        val result = networkRequest { failingApi.text() }
        val error = (result as NetworkResult.Failure).error as NetworkError.Connection

        assertEquals(cause.javaClass, error.cause.javaClass)
        assertEquals(cause.message, error.cause.message)
    }

    @Test(timeout = 10_000)
    fun callTimeoutIsDistinguishedFromConnectionFailure() = runBlocking {
        server.enqueue(
            MockResponse.Builder().headersDelay(1, TimeUnit.SECONDS).body("late").build()
        )
        val shortTimeoutClient = client.httpClient.newBuilder()
            .callTimeout(100, TimeUnit.MILLISECONDS)
            .build()
        val slowApi = NetworkClient(shortTimeoutClient)
            .createService<ClientTestApi>(server.url("/").toString())

        val result = networkRequest { slowApi.text() }

        assertTrue((result as NetworkResult.Failure).error is NetworkError.Timeout)
    }

    @Test(timeout = 10_000)
    fun coroutineCancellationCancelsTheUnderlyingHttpCall() = runBlocking {
        val callCanceled = CountDownLatch(1)
        val observedClient = client.httpClient.newBuilder()
            .eventListener(object : EventListener() {
                override fun canceled(call: Call) {
                    callCanceled.countDown()
                }
            })
            .build()
        val slowApi = NetworkClient(observedClient)
            .createService<ClientTestApi>(server.url("/").toString())
        server.enqueue(
            MockResponse.Builder().headersDelay(1, TimeUnit.SECONDS).body("late").build()
        )

        val result = async { networkRequest { slowApi.text() } }
        withContext(Dispatchers.IO) {
            checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        }
        result.cancelAndJoin()

        assertTrue(result.isCancelled)
        assertTrue(callCanceled.await(5, TimeUnit.SECONDS))
    }

    @Test(timeout = 10_000)
    fun programmingErrorsAreNotConvertedToNetworkFailures() {
        val cause = IllegalStateException("invalid application state")

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                networkRequest<String> { throw cause }
            }
        }

        assertEquals(cause.message, thrown.message)
    }
}
