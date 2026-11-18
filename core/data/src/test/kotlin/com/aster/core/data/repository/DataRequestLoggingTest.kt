package com.aster.core.data.repository

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.Logger
import com.aster.core.analytics.NoOpLogger
import java.io.IOException
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DataRequestLoggingTest {
    private val events = mutableListOf<LogEvent>()
    private val logging = DataRequestLogging(Logger { events.add(it) })
    private fun response(value: Int) = buildJsonObject { put("outcome", "SUCCESS"); put("value", value) }

    @Test
    fun observationIsLazyAndEachCollectorHasItsOwnPairedLifecycle() = runBlocking {
        var subscriptions = 0
        val flow = logging.observe(origin = "LOCAL_DATABASE", request = { buildJsonObject { put("query", "news") } },
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
            logging.observe<Int>(origin = "LOCAL_DATABASE", request = { buildJsonObject {} }, describe = ::response) {
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
        assertEquals(1, logging.observe(origin = "LOCAL_DATABASE", request = { buildJsonObject {} }, describe = ::response) {
            flowOf(1, 2)
        }.first())
        assertEquals(listOf("request_started", "request_response", "request_finished"), events.map { it.name })
        assertEquals("CANCELED", events.last().attributes["outcome"])
        assertEquals("1", events.last().response!!.jsonObject.getValue("responseCount").jsonPrimitive.content)
    }

    @Test
    fun noOpDoesNotBuildPayloadsOrCollectMoreThanOnce() = runBlocking {
        val logging = DataRequestLogging(NoOpLogger)
        val forbiddenRequest: () -> JsonObject = { error("Request must not be built") }
        val forbiddenResponse: (Int) -> JsonObject = { error("Response must not be built") }
        var subscriptions = 0
        val result: Int = logging.trace(origin = "NETWORK", operation = "FETCH", request = forbiddenRequest,
            describe = forbiddenResponse) { 7 }
        assertEquals(7, result)
        assertEquals(listOf(1, 2), logging.observe(origin = "LOCAL_DATABASE", request = forbiddenRequest,
            describe = forbiddenResponse) {
            subscriptions++
            flowOf(1, 2)
        }.toList())
        assertEquals(1, subscriptions)
    }
}
