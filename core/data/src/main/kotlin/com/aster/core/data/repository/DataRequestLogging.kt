package com.aster.core.data.repository

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.LogLevel
import com.aster.core.analytics.Logger
import com.aster.core.analytics.NoOpLogger
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsRefreshResult
import com.aster.core.localdb.entity.NewsCache
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 记录数据访问及观察订阅；日志格式及输出仍统一交给 analytics */
internal class DataRequestLogging(private val logger: Logger) {
    suspend fun <T> trace(
        sourceId: String? = null,
        origin: String,
        operation: String,
        resource: String = "news",
        request: () -> JsonObject = { buildJsonObject { sourceId?.let { put("sourceId", it) } } },
        describe: (T) -> JsonObject,
        block: suspend () -> T
    ): T {
        if (logger === NoOpLogger) return block()
        val attributes = attributes(sourceId, origin, operation, resource)
        val startedAt = System.nanoTime()
        logger.log(LogEvent("request_started", "request", attributes = attributes, request = request()))
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

    /** 一个订阅对应一个 requestId；每次发射单独打印响应，不额外收集或重新查询 */
    fun <T> observe(
        sourceId: String? = null,
        origin: String,
        operation: String = "OBSERVE",
        resource: String = "news",
        request: () -> JsonObject,
        describe: (T) -> JsonObject,
        block: () -> Flow<T>
    ): Flow<T> = flow {
        if (logger === NoOpLogger) {
            emitAll(block())
            return@flow
        }
        val attributes = attributes(sourceId, origin, operation, resource)
        val startedAt = System.nanoTime()
        var responseCount = 0L
        logger.log(LogEvent("request_started", "request", attributes = attributes, request = request()))
        try {
            block().collect { value ->
                responseCount++
                val response = describe(value)
                logger.log(LogEvent(
                    "request_response", "request",
                    attributes = attributes + mapOf(
                        "sequence" to responseCount.toString(),
                        "elapsedMs" to TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt).toString(),
                        "outcome" to response.getValue("outcome").jsonPrimitive.content
                    ),
                    response = response
                ))
                emit(value)
            }
        } catch (error: CancellationException) {
            finish(attributes, startedAt, buildJsonObject {
                put("outcome", "CANCELED")
                put("responseCount", responseCount)
            })
            throw error
        } catch (error: Exception) {
            finish(attributes, startedAt, buildJsonObject {
                put("outcome", "FAILED")
                put("errorType", error.javaClass.simpleName)
                put("responseCount", responseCount)
            })
            throw error
        }
        finish(attributes, startedAt, buildJsonObject {
            put("outcome", "COMPLETED")
            put("responseCount", responseCount)
        })
    }

    private fun attributes(sourceId: String?, origin: String, operation: String, resource: String) = buildMap {
        put("requestId", nextRequestId.incrementAndGet().toString())
        sourceId?.let { put("sourceId", it) }
        put("origin", origin)
        put("operation", operation)
        put("resource", resource)
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

internal fun NewsRefreshResult.asLogResponse(): JsonObject = when (this) {
    NewsRefreshResult.Success -> buildJsonObject { put("outcome", "SUCCESS") }
    is NewsRefreshResult.Failure -> buildJsonObject {
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
