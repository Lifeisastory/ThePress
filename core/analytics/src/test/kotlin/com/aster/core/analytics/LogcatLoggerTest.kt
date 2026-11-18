package com.aster.core.analytics

import java.net.UnknownHostException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LogcatLoggerTest {
    @Test
    fun releaseUsesNoOpAndDebugUsesLogcat() {
        assertSame(NoOpLogger, Analytics.createLogger(false))
        assertTrue(Analytics.createLogger(true) is LogcatLogger)
        NoOpLogger.log(LogEvent("ignored", "test"))
    }

    @Test
    fun outputUsesCommonFormatAndMinimumLevel() {
        val messages = mutableListOf<Pair<LogLevel, String>>()
        val logger = LogcatLogger(LogLevel.WARN) { level, message -> messages.add(level to message) }
        logger.log(LogEvent("ignored", "network"))
        logger.log(LogEvent("cache_failed", "cache", LogLevel.WARN, mapOf("sourceId" to "baidu")))
        assertEquals(listOf(LogLevel.WARN to "[cache] cache_failed sourceId=\"baidu\""), messages)
    }

    @Test
    fun credentialsAndExceptionMessagesAreNotPrintedButDnsStackIsKept() {
        val error = UnknownHostException("private-error-message")
        error.stackTrace = arrayOf(StackTraceElement("DnsLookup", "lookup", "DnsLookup.kt", 12))
        val message = LogFormatter.format(LogEvent("request_failed", "network", attributes = mapOf(
            "url" to "https://private-user:private-password@example.com/feed?token=private-query#private-fragment",
            "Authorization" to "private-header",
            "access_token" to "private-token",
            "Cookie" to "private-cookie",
            "api-key" to "private-key"
        ), cause = error))
        assertTrue(message.contains("url=\"https://example.com/feed\""))
        assertTrue(message.contains("[REDACTED]"))
        assertTrue(message.contains("java.net.UnknownHostException"))
        assertTrue(message.contains("DnsLookup.lookup(DnsLookup.kt:12)"))
        assertFalse(message.contains("private-"))
    }

    @Test
    fun malformedUrlsAndNewlinesDoNotExposeRawInputOrForgeEntries() {
        val message = LogFormatter.format(LogEvent("request_started", "network", attributes = mapOf(
            "url" to "invalid private-url",
            "sourceId" to "one\n[cache] forged\r\n"
        )))
        assertTrue(message.contains("[INVALID_URL]"))
        assertFalse(message.contains("private-url"))
        assertFalse(message.contains('\n'))
        assertTrue(message.contains("one\\n[cache] forged\\r\\n"))
    }

    @Test
    fun loggerOutputFailureDoesNotInterruptTheCaller() {
        val logger = LogcatLogger { _, _ -> throw IllegalStateException("sink unavailable") }
        logger.log(LogEvent("request_started", "network"))
    }

    @Test
    fun structuredResponsesKeepJsonTypesAndRedactNestedCredentials() {
        val event = LogEvent("request_finished", "request", response = buildJsonObject {
            put("count", 1)
            put("articles", buildJsonArray {
                add(buildJsonObject {
                    put("title", "新闻\n标题")
                    put("url", "https://example.com/article?token=private-token")
                    put("access_token", "private-token")
                })
            })
        })
        val formatted = LogFormatter.format(event)
        val response = Json.parseToJsonElement(formatted.substringAfter("response=\n"))
        assertTrue(response.toString().contains("\"count\":1"))
        assertFalse(formatted.contains("private-token"))
        assertTrue(formatted.contains("https://example.com/article"))
        assertTrue(formatted.contains("[REDACTED]"))
    }

    @Test
    fun longUnicodeResponsesArePrintedCompletelyInOrderedChunks() {
        val event = LogEvent("request_finished", "request", response = buildJsonObject {
            put("summary", "中文😀".repeat(2000))
        })
        val messages = mutableListOf<String>()
        LogcatLogger { _, message -> messages.add(message) }.log(event)
        assertTrue(messages.size > 1)
        val eventId = messages.first().substringAfter("event=").substringBefore(' ')
        messages.forEachIndexed { index, message ->
            assertTrue(message.startsWith("[event=$eventId part=${index + 1}/${messages.size}] "))
            assertTrue(message.toByteArray(Charsets.UTF_8).size < 4000)
            assertEquals(message, message.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
        }
        assertEquals(LogFormatter.format(event), messages.joinToString("") { it.substringAfter("] ") })
    }

    @Test
    fun structuredRequestsUseTheSameFormattingAndRedactionAsResponses() {
        val formatted = LogFormatter.format(LogEvent("request_started", "request", request = buildJsonObject {
            put("sourceIds", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("baidu")) })
            put("query", "新闻\n标题")
            put("options", buildJsonObject {
                put("url", "https://user:private-password@example.com/feed?token=private-token")
                put("Authorization", "private-header")
            })
        }))
        val request = Json.parseToJsonElement(formatted.substringAfter("request=\n"))
        assertTrue(request.toString().contains("\"sourceIds\":[\"baidu\"]"))
        assertTrue(formatted.contains("https://example.com/feed"))
        assertTrue(formatted.contains("[REDACTED]"))
        assertFalse(formatted.contains("private-"))
    }
}
