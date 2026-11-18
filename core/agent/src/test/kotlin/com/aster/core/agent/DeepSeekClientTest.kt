package com.aster.core.agent

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeepSeekClientTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()
    private lateinit var client: DeepSeekClient
    private val request = LlmRequest("deepseek-flash", "Return json", "input")

    @Before fun setup() {
        server = MockWebServer().also { it.start() }
        client = DeepSeekClient(http, server.url("/chat/completions"))
    }
    @After fun cleanup() {
        http.dispatcher.cancelAll(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown()
        server.close()
    }

    private fun response(content: String, finish: String = "stop") = buildJsonObject {
        put("choices", buildJsonArray { add(buildJsonObject {
            put("finish_reason", finish)
            put("message", buildJsonObject { put("content", content); put("reasoning_content", "private reasoning") })
        }) })
        put("usage", buildJsonObject { put("total_tokens", 12) })
    }.toString()

    @Test fun sendsJsonModeWithExplicitThinkingModeAndParsesUsage() = runBlocking {
        server.enqueue(MockResponse(body = response("""{"items":[]}""")))
        val result = client.generate(request, "test-key")
        assertEquals(0, result.content.getValue("items").jsonArray.size)
        assertEquals("12", result.usage.getValue("total_tokens").jsonPrimitive.content)
        assertFalse(result.toString().contains("private reasoning"))
        val sent = server.takeRequest()
        assertEquals("Bearer test-key", sent.headers["Authorization"])
        val payload = Json.parseToJsonElement(sent.body!!.utf8()).jsonObject
        assertEquals("json_object", payload.getValue("response_format").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("disabled", payload.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("deepseek-flash", payload.getValue("model").jsonPrimitive.content)
        assertEquals(false, payload.getValue("stream").jsonPrimitive.boolean)
    }

    @Test fun rejectsBlankMalformedTruncatedAndUnexpectedResponses() = runBlocking {
        listOf(
            response("  ") to LlmFailure.INVALID_RESPONSE,
            response("not json") to LlmFailure.INVALID_RESPONSE,
            response("{}", "length") to LlmFailure.TRUNCATED,
            response("{}", "tool_calls") to LlmFailure.INVALID_RESPONSE,
            "{}" to LlmFailure.INVALID_RESPONSE
        ).forEach { (body, expected) ->
            server.enqueue(MockResponse(body = body))
            try { client.generate(request, "test-key"); fail("Expected failure") }
            catch (error: LlmException) { assertEquals(expected, error.reason) }
        }
    }

    @Test fun reportsHttpCodeWithoutEchoingResponseSecrets() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).body("sensitive error body").build())
        try { client.generate(request, "test-key"); fail("Expected failure") }
        catch (error: LlmException) {
            assertEquals(LlmFailure.HTTP, error.reason); assertEquals(401, error.httpCode)
            assertFalse(error.toString().contains("sensitive"))
        }
    }

    @Test(timeout = 10_000) fun cancellingStopsInFlightCall() = runBlocking {
        server.enqueue(MockResponse.Builder().body(response("{}" )).bodyDelay(5, TimeUnit.SECONDS).build())
        val pending = launch { client.generate(request, "test-key") }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
    }
}
