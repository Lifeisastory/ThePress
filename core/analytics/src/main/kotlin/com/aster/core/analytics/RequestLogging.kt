package com.aster.core.analytics

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 记录单次请求及观察订阅；请求和响应内容由调用方提供 */
class RequestLogging(private val logger: Logger) {
    suspend fun <T> trace(
        sourceId: String? = null,
        origin: String,
        operation: String,
        resource: String,
        request: () -> JsonObject = { buildJsonObject { sourceId?.let { put("sourceId", it) } } },
        describe: (T) -> LogResponse,
        block: suspend () -> T
    ): T {
        if (logger === NoOpLogger) return block()
        val attributes = attributes(sourceId, origin, operation, resource)
        val startedAt = System.nanoTime()
        logger.log {
            LogEvent("request_started", "request", attributes = attributes, request = buildRequest(request))
        }
        val result = try {
            block()
        } catch (error: CancellationException) {
            finish(attributes, startedAt) { LogResponse(LogOutcome.CANCELED) }
            throw error
        } catch (error: Exception) {
            finish(attributes, startedAt) { LogResponse(LogOutcome.FAILED, buildJsonObject {
                put("errorType", error.javaClass.simpleName)
            }) }
            throw error
        }
        finish(attributes, startedAt) { describe(result) }
        return result
    }

    /** 一个订阅对应一个 requestId；每次发射单独打印响应，不额外收集或重新查询 */
    fun <T> observe(
        sourceId: String? = null,
        origin: String,
        operation: String = "OBSERVE",
        resource: String,
        request: () -> JsonObject,
        describe: (T) -> LogResponse,
        block: () -> Flow<T>
    ): Flow<T> = flow {
        if (logger === NoOpLogger) {
            emitAll(block())
            return@flow
        }
        val attributes = attributes(sourceId, origin, operation, resource)
        val startedAt = System.nanoTime()
        var responseCount = 0L
        logger.log {
            LogEvent("request_started", "request", attributes = attributes, request = buildRequest(request))
        }
        try {
            block().collect { value ->
                responseCount++
                logger.log {
                    val response = buildResponse { describe(value) }
                    LogEvent(
                        "request_response", "request", level = levelFor(response.outcome),
                        attributes = attributes + mapOf(
                            "sequence" to responseCount.toString(),
                            "elapsedMs" to TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt).toString(),
                            "outcome" to response.outcome.name
                        ),
                        response = response.toJsonObject()
                    )
                }
                emit(value)
            }
        } catch (error: CancellationException) {
            finish(attributes, startedAt) { LogResponse(LogOutcome.CANCELED, buildJsonObject {
                put("responseCount", responseCount)
            }) }
            throw error
        } catch (error: Exception) {
            finish(attributes, startedAt) { LogResponse(LogOutcome.FAILED, buildJsonObject {
                put("errorType", error.javaClass.simpleName)
                put("responseCount", responseCount)
            }) }
            throw error
        }
        finish(attributes, startedAt) { LogResponse(LogOutcome.COMPLETED, buildJsonObject {
            put("responseCount", responseCount)
        }) }
    }

    private fun attributes(sourceId: String?, origin: String, operation: String, resource: String) = buildMap {
        put("requestId", nextRequestId.incrementAndGet().toString())
        sourceId?.let { put("sourceId", it) }
        put("origin", origin)
        put("operation", operation)
        put("resource", resource)
    }

    private fun finish(attributes: Map<String, String>, startedAt: Long, describe: () -> LogResponse) {
        logger.log {
            val response = buildResponse(describe)
            LogEvent(
                "request_finished", "request", levelFor(response.outcome),
                attributes + mapOf(
                    "durationMs" to TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt).toString(),
                    "outcome" to response.outcome.name
                ),
                response = response.toJsonObject()
            )
        }
    }

    private fun buildRequest(request: () -> JsonObject): JsonObject = try {
        request()
    } catch (error: Exception) {
        loggingFailure(error)
    }

    private fun buildResponse(describe: () -> LogResponse): LogResponse = try {
        describe()
    } catch (error: Exception) {
        LogResponse(LogOutcome.UNKNOWN, loggingFailure(error))
    }

    private fun loggingFailure(error: Exception) = buildJsonObject {
        put("loggingErrorType", error.javaClass.simpleName)
    }

    private fun levelFor(outcome: LogOutcome) = when (outcome) {
        LogOutcome.FAILED, LogOutcome.UNKNOWN -> LogLevel.WARN
        else -> LogLevel.DEBUG
    }

    private companion object {
        val nextRequestId = AtomicLong()
    }
}
