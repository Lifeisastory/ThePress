package com.aster.core.network

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.LogLevel
import com.aster.core.analytics.Logger
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Response

/** 通过 eventListenerFactory 为每次调用创建实例，格式和输出交给 core:analytics */
class RequestLoggingListener(private val logger: Logger) : EventListener() {
    private val requestId = nextRequestId.incrementAndGet()
    private var startedAt = 0L
    private var statusCode: Int? = null

    override fun callStart(call: Call) {
        startedAt = System.nanoTime()
        val request = call.request()
        val address = request.url.newBuilder()
            .username("").password("").query(null).fragment(null).build()
        logger.log(LogEvent("request_started", "network", attributes = mapOf(
            "requestId" to requestId.toString(), "method" to request.method, "url" to address.toString()
        )))
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        statusCode = response.code
    }

    override fun callEnd(call: Call) {
        finish(if (call.isCanceled()) "CANCELED" else if ((statusCode ?: 0) >= 400) "HTTP_ERROR" else "SUCCESS")
    }

    override fun callFailed(call: Call, ioe: IOException) {
        finish(if (call.isCanceled()) "CANCELED" else "FAILED", ioe.javaClass.simpleName)
    }

    private fun finish(outcome: String, errorType: String? = null) {
        val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        logger.log(LogEvent(
            name = "request_finished",
            category = "network",
            level = if (outcome in listOf("FAILED", "HTTP_ERROR")) LogLevel.WARN else LogLevel.DEBUG,
            attributes = buildMap {
                put("requestId", requestId.toString())
                put("outcome", outcome)
                put("status", statusCode?.toString() ?: "-")
                put("durationMs", duration.toString())
                errorType?.let { put("errorType", it) }
            }
        ))
    }

    private companion object {
        val nextRequestId = AtomicLong()
    }
}
