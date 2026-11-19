package com.aster.core.agent

import com.aster.core.agent.runtime.*
import com.aster.core.network.EventStreamClient
import com.aster.core.network.EventStreamException
import com.aster.core.network.EventStreamFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.serialization.json.*

/** DeepSeek 流式工具协议适配，HTTP 连接和 SSE 拆帧由 network 管理 */
class DeepSeekAgentClient(
    private val transport: EventStreamClient = EventStreamClient(),
    private val endpoint: String = "https://api.deepseek.com/chat/completions"
) : AgentModelClient {
    override fun stream(request: AgentModelRequest, apiKey: String): Flow<AgentModelChunk> = flow {
        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("thinking", buildJsonObject { put("type", "disabled") })
            put("max_tokens", 8192)
            put("messages", JsonArray(request.messages.map { message -> buildJsonObject {
                put("role", message.role)
                put("content", message.content)
                message.toolCallId?.let { put("tool_call_id", it) }
                if (message.toolCalls.isNotEmpty()) put("tool_calls", JsonArray(message.toolCalls.map { call ->
                    buildJsonObject {
                        put("id", call.id)
                        put("type", "function")
                        put("function", buildJsonObject { put("name", call.name); put("arguments", call.arguments) })
                    }
                }))
            } }))
            if (request.tools.isNotEmpty()) put("tools", JsonArray(request.tools.map { tool -> buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", tool.name); put("description", tool.description); put("parameters", tool.parameters)
                })
            } }))
        }
        var done = false
        var finished = false
        try {
            transport.post(endpoint, payload.toString(), mapOf("Authorization" to "Bearer $apiKey"))
                .takeWhile { frame ->
                    if (frame.trim() == "[DONE]") { done = true; false } else true
                }.collect { frame ->
                    val chunks: List<AgentModelChunk> = try {
                        val root = Json.parseToJsonElement(frame).jsonObject
                        require(root["error"] == null)
                        val choices = root.getValue("choices").jsonArray
                        buildList<AgentModelChunk> {
                            for (value in choices) {
                                val choice = value.jsonObject
                                require(choice["index"]?.jsonPrimitive?.intOrNull == 0)
                                require(!finished)
                                val delta = choice["delta"]?.jsonObject ?: buildJsonObject {}
                                delta["content"]?.jsonPrimitive?.contentOrNull?.let { add(AgentModelChunk.Text(it)) }
                                delta["tool_calls"]?.jsonArray?.forEach { value ->
                                    val call = value.jsonObject
                                    val function = call["function"]?.jsonObject
                                    add(AgentModelChunk.Tool(
                                        call.getValue("index").jsonPrimitive.int,
                                        call["id"]?.jsonPrimitive?.contentOrNull,
                                        function?.get("name")?.jsonPrimitive?.contentOrNull,
                                        function?.get("arguments")?.jsonPrimitive?.contentOrNull
                                    ))
                                }
                                choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let {
                                    finished = true
                                    add(AgentModelChunk.Finished(it))
                                }
                            }
                        }
                    } catch (_: Exception) { throw LlmException(LlmFailure.INVALID_RESPONSE) }
                    chunks.forEach { emit(it) }
                }
            if (!done || !finished) throw LlmException(LlmFailure.TRUNCATED)
        } catch (error: CancellationException) {
            throw error
        } catch (error: EventStreamException) {
            throw LlmException(when (error.reason) {
                EventStreamFailure.CONNECTION -> LlmFailure.CONNECTION
                EventStreamFailure.TIMEOUT -> LlmFailure.TIMEOUT
                EventStreamFailure.HTTP -> LlmFailure.HTTP
                EventStreamFailure.INVALID_RESPONSE -> LlmFailure.INVALID_RESPONSE
            }, error.httpCode)
        }
    }
}
