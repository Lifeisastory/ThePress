package com.aster.core.agent

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** 独立配置 LLM 超时；供应商适配层不依赖新闻业务、存储或页面 */
class DeepSeekClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS).retryOnConnectionFailure(false).build(),
    private val endpoint: HttpUrl = "https://api.deepseek.com/chat/completions".toHttpUrl()
) : LlmClient {
    override suspend fun generate(request: LlmRequest, apiKey: String): LlmResponse {
        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", false)
            put("thinking", buildJsonObject { put("type", "disabled") })
            put("max_tokens", 16384)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", request.system) })
                add(buildJsonObject { put("role", "user"); put("content", request.user) })
            })
        }
        val call = http.newCall(Request.Builder().url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build())
        val body = suspendCancellableCoroutine<String> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(LlmException(
                        if (e is InterruptedIOException) LlmFailure.TIMEOUT else LlmFailure.CONNECTION
                    ))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use {
                            if (!it.isSuccessful) throw LlmException(LlmFailure.HTTP, it.code)
                            it.body.string()
                        }
                        if (continuation.isActive) continuation.resume(text)
                    } catch (error: Exception) {
                        val failure = error as? LlmException
                            ?: LlmException(
                                if (error is InterruptedIOException) LlmFailure.TIMEOUT else LlmFailure.CONNECTION
                            )
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            })
        }
        try {
            val response = Json.parseToJsonElement(body).jsonObject
            val choice = response.getValue("choices").jsonArray.first().jsonObject
            val finish = choice.getValue("finish_reason").jsonPrimitive.content
            if (finish == "length") throw LlmException(LlmFailure.TRUNCATED)
            if (finish != "stop") throw LlmException(LlmFailure.INVALID_RESPONSE)
            val content = choice.getValue("message").jsonObject.getValue("content").jsonPrimitive
            if (!content.isString || content.content.isBlank()) throw LlmException(LlmFailure.INVALID_RESPONSE)
            return LlmResponse(Json.parseToJsonElement(content.content).jsonObject,
                response["usage"] as? JsonObject ?: buildJsonObject {})
        } catch (error: CancellationException) {
            throw error
        } catch (error: LlmException) {
            throw error
        } catch (_: Exception) {
            throw LlmException(LlmFailure.INVALID_RESPONSE)
        }
    }
}
