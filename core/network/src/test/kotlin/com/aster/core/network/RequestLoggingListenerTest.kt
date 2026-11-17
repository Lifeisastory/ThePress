package com.aster.core.network

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.LogLevel
import com.aster.core.analytics.Logger
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestLoggingListenerTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val logs = CopyOnWriteArrayList<LogEvent>()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = NetworkClient(logger = Logger(logs::add)).httpClient
    }

    @After
    fun tearDown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        server.close()
    }

    @Test(timeout = 10_000)
    fun successfulRequestLogsStartAndEndAfterBodyReadWithoutSecrets() {
        server.enqueue(MockResponse(body = "private-response"))
        val request = Request.Builder()
            .url(server.url("/feed?token=private-query#private-fragment").newBuilder()
                .username("private-user").password("private-password").build())
            .header("Authorization", "Bearer private-header").build()
        client.newCall(request).execute().use {
            assertEquals(1, logs.size)
            assertEquals("GET", logs.first().attributes["method"])
            assertEquals(server.url("/feed").toString(), logs.first().attributes["url"])
            assertEquals("private-response", it.body.string())
        }
        assertPair("SUCCESS", "200")
        assertFalse(logs.joinToString().contains("private-"))
    }

    @Test(timeout = 10_000)
    fun httpFailureLogsEndAndDistinctRequestsHaveDistinctIds() {
        repeat(2) {
            server.enqueue(MockResponse(code = 503, body = "unavailable"))
            client.newCall(Request.Builder().url(server.url("/feed")).build()).execute().use { it.body.string() }
        }
        assertEquals(4, logs.size)
        listOf(logs[1], logs[3]).forEach {
            assertEquals("HTTP_ERROR", it.attributes["outcome"])
            assertEquals("503", it.attributes["status"])
            assertEquals(LogLevel.WARN, it.level)
        }
        assertFalse(logs[0].attributes["requestId"] == logs[2].attributes["requestId"])
    }

    @Test(timeout = 10_000)
    fun dnsFailureStillLogsEnd() {
        val failing = client.newBuilder().dns { throw UnknownHostException("private-error") }.build()
        assertThrows(UnknownHostException::class.java) {
            failing.newCall(Request.Builder().url("https://dns.test.invalid/feed").build()).execute()
        }
        assertPair("FAILED", "-")
        assertEquals("UnknownHostException", logs.last().attributes["errorType"])
        assertFalse(logs.joinToString().contains("private-error"))
    }

    @Test(timeout = 10_000)
    fun canceledRequestStillLogsEnd() {
        val call = client.newCall(Request.Builder().url(server.url("/feed")).build())
        val finished = CountDownLatch(1)
        call.cancel()
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { finished.countDown() }
            override fun onResponse(call: Call, response: Response) { response.close(); finished.countDown() }
        })
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertPair("CANCELED", "-")
    }

    private fun assertPair(outcome: String, status: String) {
        assertEquals(2, logs.size)
        assertEquals("request_started", logs.first().name)
        assertEquals("request_finished", logs.last().name)
        assertEquals("network", logs.first().category)
        assertEquals(logs.first().attributes["requestId"], logs.last().attributes["requestId"])
        assertEquals(outcome, logs.last().attributes["outcome"])
        assertEquals(status, logs.last().attributes["status"])
        assertTrue(logs.last().attributes.getValue("durationMs").toLong() >= 0)
    }
}
