package com.aster.core.data.repository

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.Logger
import java.io.IOException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultUserPreferencesRepositoryTest {
    private val events = mutableListOf<LogEvent>()
    private val logger = Logger { events.add(it) }

    @Test
    fun preferencesObservationAndWriteHaveLocalOriginsAndStructuredPayloads() = runBlocking {
        var saved = emptyList<String>()
        val repository = DefaultUserPreferencesRepository(
            flowOf(emptyList(), listOf("two", "one")), { saved = it }, logger
        )
        assertEquals(listOf(emptyList<String>(), listOf("two", "one")), repository.sourceOrder.toList())
        repository.saveSourceOrder(listOf("two", "", "one", "two"))
        assertEquals(listOf("two", "one"), saved)
        assertTrue(events.all { it.attributes["origin"] == "LOCAL_PREFERENCES" })
        assertTrue(events.all { it.attributes["resource"] == "user_preferences" })
        assertTrue(events.all { "sourceId" !in it.attributes })
        val observation = events.filter { it.attributes["operation"] == "OBSERVE" }
        assertEquals(listOf("request_started", "request_response", "request_response", "request_finished"), observation.map { it.name })
        assertEquals("news_source_order", observation.first().request!!.jsonObject.getValue("key").jsonPrimitive.content)
        assertEquals("0", observation[1].response!!.jsonObject.getValue("count").jsonPrimitive.content)
        val write = events.filter { it.attributes["operation"] == "WRITE" }
        assertEquals(listOf("request_started", "request_finished"), write.map { it.name })
        assertEquals(write.first().attributes["requestId"], write.last().attributes["requestId"])
        assertEquals(listOf("two", "one"), write.first().request!!.jsonObject.getValue("sourceIds").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("two", "one"), write.last().response!!.jsonObject.getValue("sourceIds").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun writeFailureLogsFailureAndPropagatesWithoutChangingStoredOrder() = runBlocking {
        val failure = IOException("disk full")
        val repository = DefaultUserPreferencesRepository(flowOf(listOf("one")), { throw failure }, logger)
        try {
            repository.saveSourceOrder(listOf("two"))
            throw AssertionError("Expected failure")
        } catch (error: IOException) {
            assertSame(failure, error)
        }
        assertEquals(listOf("request_started", "request_finished"), events.map { it.name })
        assertEquals("FAILED", events.last().attributes["outcome"])
        assertEquals("IOException", events.last().response!!.jsonObject.getValue("errorType").jsonPrimitive.content)
        assertEquals(listOf(listOf("one")), repository.sourceOrder.toList())
    }

    @Test
    fun readFailureLogsFailureAndPropagates() = runBlocking {
        val failure = IOException("unavailable")
        val repository = DefaultUserPreferencesRepository(flow { throw failure }, {}, logger)
        try {
            repository.sourceOrder.toList()
            throw AssertionError("Expected failure")
        } catch (error: IOException) {
            assertSame(failure, error)
        }
        assertEquals(listOf("request_started", "request_finished"), events.map { it.name })
        assertEquals("LOCAL_PREFERENCES", events.last().attributes["origin"])
        assertEquals("FAILED", events.last().attributes["outcome"])
    }
}
