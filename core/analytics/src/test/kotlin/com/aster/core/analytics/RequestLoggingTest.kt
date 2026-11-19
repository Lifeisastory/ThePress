package com.aster.core.analytics

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestLoggingTest {
    private val events = mutableListOf<LogEvent>()
    private val logging = RequestLogging(Logger { events.add(it) })
    private fun response(value: Int) = LogResponse(LogOutcome.SUCCESS, buildJsonObject { put("value", value) })

    @Test
    fun observationIsLazyAndEachCollectorHasItsOwnPairedLifecycle() = runBlocking {
        var subscriptions = 0
        val flow = logging.observe(origin = "LOCAL_DATABASE", resource = "news", request = { buildJsonObject { put("query", "news") } },
            describe = ::response) {
            subscriptions++
            flowOf(1, 2)
        }
        assertTrue(events.isEmpty())
        repeat(2) { assertEquals(listOf(1, 2), flow.toList()) }
        assertEquals(2, subscriptions)
        val requests = events.groupBy { it.attributes.getValue("requestId") }
        assertEquals(2, requests.size)
        requests.values.forEach { request ->
            assertEquals(listOf("request_started", "request_response", "request_response", "request_finished"), request.map { it.name })
            assertEquals(listOf("1", "2"), request.filter { it.name == "request_response" }.map { it.attributes["sequence"] })
            assertEquals("COMPLETED", request.last().attributes["outcome"])
            assertEquals("2", request.last().response!!.jsonObject.getValue("responseCount").jsonPrimitive.content)
        }
    }

    @Test
    fun observationFailureIsLoggedAndOriginalExceptionPropagates() = runBlocking {
        val failure = IOException("unavailable")
        try {
            logging.observe<Int>(origin = "LOCAL_DATABASE", resource = "news", request = { buildJsonObject {} }, describe = ::response) {
                throw failure
            }.toList()
            throw AssertionError("Expected failure")
        } catch (error: IOException) {
            assertSame(failure, error)
        }
        assertEquals(listOf("request_started", "request_finished"), events.map { it.name })
        assertEquals("FAILED", events.last().attributes["outcome"])
        assertEquals("IOException", events.last().response!!.jsonObject.getValue("errorType").jsonPrimitive.content)
    }

    @Test
    fun firstCancelsSubscriptionWithoutLosingTheResponse() = runBlocking {
        assertEquals(1, logging.observe(origin = "LOCAL_DATABASE", resource = "news", request = { buildJsonObject {} }, describe = ::response) {
            flowOf(1, 2)
        }.first())
        assertEquals(listOf("request_started", "request_response", "request_finished"), events.map { it.name })
        assertEquals("CANCELED", events.last().attributes["outcome"])
        assertEquals("1", events.last().response!!.jsonObject.getValue("responseCount").jsonPrimitive.content)
    }

    @Test
    fun noOpDoesNotBuildPayloadsOrCollectMoreThanOnce() = runBlocking {
        val logging = RequestLogging(NoOpLogger)
        val forbiddenRequest: () -> JsonObject = { error("Request must not be built") }
        val forbiddenResponse: (Int) -> LogResponse = { error("Response must not be built") }
        NoOpLogger.log { error("Event must not be built") }
        var subscriptions = 0
        val result: Int = logging.trace(origin = "NETWORK", operation = "FETCH", resource = "news", request = forbiddenRequest,
            describe = forbiddenResponse) { 7 }
        assertEquals(7, result)
        assertEquals(listOf(1, 2), logging.observe(origin = "LOCAL_DATABASE", resource = "news", request = forbiddenRequest,
            describe = forbiddenResponse) {
            subscriptions++
            flowOf(1, 2)
        }.toList())
        assertEquals(1, subscriptions)
    }

    @Test
    fun traceFailureAndCancellationKeepOriginalExceptions() = runBlocking {
        listOf(IOException("failure"), CancellationException("canceled")).forEach { failure ->
            events.clear()
            try {
                logging.trace<Unit>(origin = "NETWORK", operation = "FETCH", resource = "news", describe = { response(0) }) {
                    throw failure
                }
                throw AssertionError("Expected failure")
            } catch (error: Exception) {
                assertSame(failure, error)
            }
            assertEquals(listOf("request_started", "request_finished"), events.map { it.name })
            val outcome = if (failure is CancellationException) "CANCELED" else "FAILED"
            assertEquals(outcome, events.last().attributes["outcome"])
            assertEquals(events.first().attributes["requestId"], events.last().attributes["requestId"])
        }
    }

    @Test
    fun requestAndDescriptionFailuresDoNotChangeSuccessfulResult() = runBlocking {
        listOf(IOException("private-log-error"), CancellationException("private-log-error")).forEach { logFailure ->
            events.clear()
            val expected = Any()
            var executions = 0
            val result = logging.trace(origin = "LOCAL_DATABASE", operation = "WRITE", resource = "news",
                request = { throw logFailure }, describe = { _: Any -> throw logFailure }) {
                executions++
                expected
            }
            assertSame(expected, result)
            assertEquals(1, executions)
            assertEquals(listOf("request_started", "request_finished"), events.map { it.name })
            assertEquals(logFailure.javaClass.simpleName,
                events.first().request!!.jsonObject.getValue("loggingErrorType").jsonPrimitive.content)
            assertEquals("UNKNOWN", events.last().attributes["outcome"])
            assertEquals(LogLevel.WARN, events.last().level)
            assertEquals(logFailure.javaClass.simpleName,
                events.last().response!!.jsonObject.getValue("loggingErrorType").jsonPrimitive.content)
            assertFalse(events.toString().contains("private-log-error"))
        }
    }

    @Test
    fun outputFailuresDoNotChangeSuccessfulResult() = runBlocking {
        listOf(IOException("sink failed"), CancellationException("sink canceled")).forEach { logFailure ->
            val brokenLogging = RequestLogging(Logger { throw logFailure })
            var executions = 0
            val expected = Any()
            val result = brokenLogging.trace(origin = "LOCAL_DATABASE", operation = "WRITE", resource = "news",
                describe = { _: Any -> LogResponse(LogOutcome.SUCCESS) }) {
                executions++
                expected
            }
            assertSame(expected, result)
            assertEquals(1, executions)
        }
    }

    @Test
    fun loggingFailuresCannotReplaceBusinessFailureOrCancellation() = runBlocking {
        val brokenLogging = RequestLogging(Logger { throw CancellationException("logging failure") })
        listOf(IOException("business failure"), CancellationException("business canceled")).forEach { failure ->
            var executions = 0
            try {
                brokenLogging.trace<Unit>(origin = "NETWORK", operation = "FETCH", resource = "news",
                    request = { error("request description failed") },
                    describe = { throw AssertionError("Failed business has no result to describe") }) {
                    executions++
                    throw failure
                }
                throw AssertionError("Expected business failure")
            } catch (error: Exception) {
                assertSame(failure, error)
            }
            assertEquals(1, executions)
        }
    }

    @Test
    fun flowDeliversAllValuesOnceDespiteLoggingFailures() = runBlocking {
        val brokenLogging = RequestLogging(Logger { event ->
            events.add(event)
            throw CancellationException("sink failure")
        })
        var subscriptions = 0
        var descriptions = 0
        val values = brokenLogging.observe(origin = "LOCAL_DATABASE", resource = "news",
            request = { error("request description failed") }, describe = { value: Int ->
                descriptions++
                if (value == 2) error("response description failed")
                response(value)
            }) {
            subscriptions++
            flowOf(1, 2, 3)
        }.toList()
        assertEquals(listOf(1, 2, 3), values)
        assertEquals(1, subscriptions)
        assertEquals(3, descriptions)
        assertEquals(listOf("SUCCESS", "UNKNOWN", "SUCCESS"),
            events.filter { it.name == "request_response" }.map { it.attributes["outcome"] })
        assertEquals("COMPLETED", events.last().attributes["outcome"])
        assertEquals("3", events.last().response!!.jsonObject.getValue("responseCount").jsonPrimitive.content)
    }

    @Test
    fun flowFailureAndCancellationKeepTheirIdentityWithBrokenLogging() = runBlocking {
        val brokenLogging = RequestLogging(Logger { error("sink failure") })
        listOf(IOException("source failure"), CancellationException("source canceled")).forEach { failure ->
            val values = mutableListOf<Int>()
            var subscriptions = 0
            try {
                brokenLogging.observe<Int>(origin = "LOCAL_DATABASE", resource = "news",
                    request = { error("request description failed") }, describe = { error("response description failed") }) {
                    subscriptions++
                    flow { emit(1); throw failure }
                }.toList(values)
                throw AssertionError("Expected source failure")
            } catch (error: Exception) {
                assertSame(failure, error)
            }
            assertEquals(listOf(1), values)
            assertEquals(1, subscriptions)
        }
    }

    @Test(timeout = 10_000)
    fun actualCoroutineCancellationStillStopsBusinessWithBrokenLogging() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val brokenLogging = RequestLogging(Logger { error("sink failure") })
        val operation = launch {
            brokenLogging.trace<Unit>(origin = "NETWORK", operation = "FETCH", resource = "news",
                describe = { response(0) }) {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        operation.cancelAndJoin()
        assertTrue(operation.isCancelled)
    }

    @Test
    fun explicitOutcomeControlsSerializedStatusAndLevel() = runBlocking {
        val result = logging.trace(origin = "NETWORK", operation = "FETCH", resource = "news",
            describe = { _: Int -> LogResponse(LogOutcome.FAILED, buildJsonObject {
                put("outcome", "SUCCESS")
                put("reason", "HTTP")
            }) }) { 7 }
        assertEquals(7, result)
        val event = events.last()
        assertEquals("FAILED", event.attributes["outcome"])
        assertEquals(LogLevel.WARN, event.level)
        assertEquals("FAILED", event.response!!.jsonObject.getValue("outcome").jsonPrimitive.content)
        assertEquals("HTTP", event.response!!.jsonObject.getValue("reason").jsonPrimitive.content)
    }
}
