package com.aster.core.network

import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resumeWithException

enum class EventStreamFailure { CONNECTION, TIMEOUT, HTTP, INVALID_RESPONSE }

class EventStreamException(val reason: EventStreamFailure, val httpCode: Int? = null) :
    Exception("Event stream ${reason.name}")

/** 只处理 HTTP 和 SSE 帧，不解释模型或工具协议 */
class EventStreamClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
) {
    fun post(url: String, body: String, headers: Map<String, String>): Flow<String> = flow {
        val request = Request.Builder().url(url)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "text/event-stream")
            .apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        val call = http.newCall(request)
        try {
            coroutineScope {
                // 读取响应体时也要关闭连接，不能只取消等待响应头的 continuation
                val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { call.cancel() }
                }
                try {
                    call.awaitResponse().use { response ->
                        if (!response.isSuccessful) throw EventStreamException(EventStreamFailure.HTTP, response.code)
                        if (response.body.contentType()?.subtype != "event-stream") {
                            throw EventStreamException(EventStreamFailure.INVALID_RESPONSE)
                        }
                        val source = response.body.source()
                        val data = StringBuilder()
                        while (!source.exhausted()) {
                            currentCoroutineContext().ensureActive()
                            val line = source.readUtf8LineStrict(262_144)
                            when {
                                line.isEmpty() -> if (data.isNotEmpty()) {
                                    emit(data.toString().removeSuffix("\n"))
                                    data.setLength(0)
                                }
                                line.startsWith("data:") -> {
                                    data.append(line.removePrefix("data:").removePrefix(" ")).append('\n')
                                    if (data.length > 262_144) throw EventStreamException(EventStreamFailure.INVALID_RESPONSE)
                                }
                            }
                        }
                        // 没有空行结束的帧视为断流，交给调用方处理完整性
                        if (data.isNotEmpty()) throw EventStreamException(EventStreamFailure.INVALID_RESPONSE)
                    }
                } finally {
                    cancellation.cancel()
                    call.cancel()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            throw EventStreamException(when (error) {
                is EOFException -> EventStreamFailure.INVALID_RESPONSE
                is InterruptedIOException -> EventStreamFailure.TIMEOUT
                else -> EventStreamFailure.CONNECTION
            })
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }
}
