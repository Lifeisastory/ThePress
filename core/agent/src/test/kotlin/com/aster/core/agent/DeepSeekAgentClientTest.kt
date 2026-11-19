package com.aster.core.agent

import com.aster.core.agent.runtime.*
import com.aster.core.agent.skills.AgentSkill
import com.aster.core.agent.skills.AgentSkillStore
import com.aster.core.model.AgentEvent
import com.aster.core.network.EventStreamClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeepSeekAgentClientTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()
    private lateinit var client: DeepSeekAgentClient
    private val request = AgentModelRequest("deepseek-flash", listOf(AgentModelMessage("user", "hello")), emptyList())

    @Before fun setup() {
        server = MockWebServer().also { it.start() }
        client = DeepSeekAgentClient(EventStreamClient(http), server.url("/chat/completions").toString())
    }

    @After fun cleanup() {
        http.dispatcher.cancelAll(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown()
        server.close()
    }

    private fun frame(delta: JsonObject = buildJsonObject {}, finish: String? = null) = "data: " + buildJsonObject {
        put("choices", buildJsonArray { add(buildJsonObject {
            put("index", 0); put("delta", delta); put("finish_reason", finish?.let(::JsonPrimitive) ?: JsonNull)
        }) })
    } + "\n\n"

    private fun toolFrame(id: String?, name: String?, args: String) = frame(buildJsonObject {
        put("tool_calls", buildJsonArray { add(buildJsonObject {
            put("index", 0)
            id?.let { put("id", it) }
            put("function", buildJsonObject { name?.let { put("name", it) }; put("arguments", args) })
        }) })
    })

    private fun enqueue(body: String) = server.enqueue(MockResponse.Builder()
        .addHeader("Content-Type", "text/event-stream; charset=utf-8").body(body).build())

    private fun enqueuePausedStream(first: String): CountDownLatch {
        val gate = CountDownLatch(1)
        val tail = frame(finish = "stop") + "data: [DONE]\n\n"
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream")
            .body(object : MockResponseBody {
                override val contentLength = (first + tail).toByteArray().size.toLong()
                override fun writeTo(sink: okio.BufferedSink) {
                    sink.writeUtf8(first).flush()
                    gate.await(5, TimeUnit.SECONDS)
                    sink.writeUtf8(tail).flush()
                }
            }).build())
        return gate
    }

    @Test(timeout = 10_000) fun deliversFirstTokenBeforeTheResponseFinishes() = runBlocking {
        val first = frame(buildJsonObject { put("content", "你好") })
        val gate = enqueuePausedStream(first)
        try {
            val result = withTimeout(2000) { client.stream(request, "test-key").first() }
            assertEquals(AgentModelChunk.Text("你好"), result)
        } finally { gate.countDown() }
    }

    @Test(timeout = 10_000) fun mergesToolArgumentsLoadsSkillAndSendsToolResultBackBeforeFinalText() = runBlocking {
        enqueue(": keepalive\n\n" + toolFrame("skill-1", "use_skill", "{\"name\":") +
            toolFrame(null, null, "\"news-briefing\"}") + frame(finish = "tool_calls") + "data: [DONE]\n\n")
        enqueue(frame(buildJsonObject { put("content", "整理完成") }) + frame(finish = "stop") + "data: [DONE]\n\n")
        var reads = 0
        val store = object : AgentSkillStore {
            override suspend fun list() = listOf(AgentSkill("news-briefing", "每日简报"))
            override suspend fun read(name: String, path: String): String { reads++; return "只根据来源写作" }
        }
        val events = StreamingAgent(client, store).run("deepseek-flash", "test", request.messages, emptyList(), "test-key").toList()
        assertEquals(1, reads)
        assertTrue(events.first() is AgentEvent.StepStarted)
        assertEquals(AgentEvent.Completed, events.last())
        assertEquals("整理完成", events.filterIsInstance<AgentEvent.TextDelta>().joinToString("") { it.text })
        val first = server.takeRequest()
        assertEquals("Bearer test-key", first.headers["Authorization"])
        val initial = Json.parseToJsonElement(first.body!!.utf8()).jsonObject
        assertTrue(initial.getValue("stream").jsonPrimitive.boolean)
        assertNull(initial["response_format"])
        val next = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        val messages = next.getValue("messages").jsonArray
        assertEquals("tool", messages.last().jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("skill-1", messages.last().jsonObject.getValue("tool_call_id").jsonPrimitive.content)
        assertEquals("只根据来源写作", messages.last().jsonObject.getValue("content").jsonPrimitive.content)
    }

    @Test(timeout = 10_000) fun refusesPrematureEofAndDoesNotRunAnIncompleteToolCall() = runBlocking {
        enqueue(toolFrame("unfinished", "use_skill", "{\"name\":\"news-briefing\"}") + frame(finish = "tool_calls"))
        var reads = 0
        val store = object : AgentSkillStore {
            override suspend fun list() = emptyList<AgentSkill>()
            override suspend fun read(name: String, path: String): String { reads++; return "" }
        }
        try {
            StreamingAgent(client, store).run("model", "system", request.messages, emptyList(), "key").toList()
            fail("Expected truncated stream")
        } catch (error: LlmException) { assertEquals(LlmFailure.TRUNCATED, error.reason) }
        assertEquals(0, reads)
    }

    @Test(timeout = 10_000) fun cancellationClosesAStreamWaitingForMoreBytes() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val first = frame(buildJsonObject { put("content", "partial") })
        val gate = enqueuePausedStream(first)
        try {
            val job = launch { client.stream(request, "key").collect { started.complete(Unit) } }
            withTimeout(2000) { started.await() }
            withTimeout(1000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        } finally { gate.countDown() }
    }

    @Test fun httpErrorsExposeOnlyTheStatusCode() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).body("secret response").build())
        try { client.stream(request, "secret-key").toList(); fail("Expected HTTP error") }
        catch (error: LlmException) {
            assertEquals(401, error.httpCode)
            assertFalse(error.toString().contains("secret"))
        }
    }
}
