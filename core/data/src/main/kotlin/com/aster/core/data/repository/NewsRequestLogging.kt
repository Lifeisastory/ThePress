package com.aster.core.data.repository

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.LogLevel
import com.aster.core.analytics.Logger
import com.aster.core.analytics.NoOpLogger
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsLoadResult
import com.aster.core.localdb.entity.NewsCache
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 记录一次实际数据访问；日志格式及输出仍统一交给 analytics */
internal class NewsRequestLogging(private val logger: Logger) {
    suspend fun <T> trace(
        sourceId: String,
        origin: String,
        operation: String,
        describe: (T) -> JsonObject,
        block: suspend () -> T
    ): T {
        if (logger === NoOpLogger) return block()
        val attributes = mapOf(
            "requestId" to nextRequestId.incrementAndGet().toString(),
            "sourceId" to sourceId,
            "origin" to origin,
            "operation" to operation
        )
        val startedAt = System.nanoTime()
        logger.log(LogEvent("request_started", "request", attributes = attributes))
        try {
            val result = block()
            finish(attributes, startedAt, describe(result))
            return result
        } catch (error: CancellationException) {
            finish(attributes, startedAt, buildJsonObject { put("outcome", "CANCELED") })
            throw error
        } catch (error: Exception) {
            finish(attributes, startedAt, buildJsonObject {
                put("outcome", "FAILED")
                put("errorType", error.javaClass.simpleName)
            })
            throw error
        }
    }

    private fun finish(attributes: Map<String, String>, startedAt: Long, response: JsonObject) {
        val outcome = response.getValue("outcome").jsonPrimitive.content
        logger.log(LogEvent(
            "request_finished", "request",
            if (outcome == "FAILED") LogLevel.WARN else LogLevel.DEBUG,
            attributes + mapOf(
                "durationMs" to TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt).toString(),
                "outcome" to outcome
            ),
            response = response
        ))
    }

    private companion object {
        val nextRequestId = AtomicLong()
    }
}

internal fun List<NewsArticle>.asLogResponse(outcome: String = "SUCCESS"): JsonObject = buildJsonObject {
    put("outcome", outcome)
    put("count", size)
    put("articles", JsonArray(map { article -> buildJsonObject {
        put("sourceId", article.sourceId)
        put("id", article.id)
        put("title", article.title)
        put("url", article.url)
        put("summary", article.summary?.let(::JsonPrimitive) ?: JsonNull)
        put("imageUrl", article.imageUrl?.let(::JsonPrimitive) ?: JsonNull)
    } }))
}

internal fun NewsLoadResult.asLogResponse(): JsonObject = when (this) {
    is NewsLoadResult.Success -> articles.asLogResponse()
    is NewsLoadResult.Failure -> buildJsonObject {
        put("outcome", "FAILED")
        put("reason", reason.name)
        put("httpCode", httpCode?.let(::JsonPrimitive) ?: JsonNull)
    }
}

internal fun NewsCache?.asLogResponse(fresh: Boolean): JsonObject = if (this == null) {
    buildJsonObject { put("outcome", "MISS") }
} else {
    val articles = articles.map { NewsArticle(it.sourceId, it.id, it.title, it.url, it.summary, it.imageUrl) }
    JsonObject(articles.asLogResponse(if (fresh) "HIT" else "EXPIRED") +
        ("fetchedAt" to JsonPrimitive(metadata.fetchedAt)))
}
