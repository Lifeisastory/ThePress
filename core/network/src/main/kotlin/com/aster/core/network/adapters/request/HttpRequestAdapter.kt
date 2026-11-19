package com.aster.core.network.adapters.request

import com.aster.core.network.adapters.AdapterException
import com.aster.core.network.dto.HttpPayload
import com.aster.core.network.model.HttpMethod
import com.aster.core.network.model.HttpRequestSpec
import com.aster.core.model.ProcessingStage
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers.Companion.toHeaders
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import retrofit2.HttpException

/** 动态 HTTP 请求，复用 NetworkClient 的连接池并在取消时关闭底层调用 */
class HttpRequestAdapter(private val client: OkHttpClient) {
    suspend fun execute(spec: HttpRequestSpec): HttpPayload = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(spec.toRequest())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val payload = try {
                    response.use {
                        if (!it.isSuccessful && it.code !in spec.acceptedStatusCodes) {
                            // 错误正文也限定大小，并交给既有 networkRequest 转换 HTTP 错误
                            throw HttpException(
                                retrofit2.Response.error<String>(it.peekBody(spec.maxResponseBytes), it)
                            )
                        }
                        if (it.body.contentLength() > spec.maxResponseBytes ||
                            it.body.source().request(spec.maxResponseBytes + 1)
                        ) {
                            throw AdapterException(ProcessingStage.REQUEST, "响应超过 ${spec.maxResponseBytes} 字节限制")
                        }
                        val body = spec.responseCharset?.let { charset -> it.body.source().readString(charset) }
                            ?: it.body.string()
                        HttpPayload(it.request.url, it.headers, body)
                    }
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                    return
                }
                // 返回的是已读取的文本；即使此时协程取消，也不会遗留未关闭的响应体
                continuation.resume(payload)
            }
        })
    }
}

private fun HttpRequestSpec.toRequest(): Request {
    val target = url.newBuilder().apply {
        query.forEach { (name, value) -> addQueryParameter(name, value) }
    }.build()
    return Request.Builder()
        .url(target)
        .headers(headers.toHeaders())
        .apply {
            when (method) {
                HttpMethod.GET -> get()
                HttpMethod.POST -> post(
                    formBody?.let { fields ->
                        FormBody.Builder().apply { fields.forEach { (name, value) -> add(name, value) } }.build()
                    } ?: jsonBody?.toString()
                        ?.toRequestBody("application/json; charset=utf-8".toMediaType())
                        ?: "".toRequestBody()
                )
            }
        }
        .build()
}
