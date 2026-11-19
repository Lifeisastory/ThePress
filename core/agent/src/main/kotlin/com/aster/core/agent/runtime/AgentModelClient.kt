package com.aster.core.agent.runtime

import com.aster.core.model.AgentStepKind
import com.aster.core.model.DigestReference
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

data class AgentToolCall(val id: String, val name: String, val arguments: String)

data class AgentModelMessage(
    val role: String,
    val content: String,
    val toolCalls: List<AgentToolCall> = emptyList(),
    val toolCallId: String? = null
)

data class AgentToolResult(
    val content: String,
    val references: List<DigestReference> = emptyList(),
    val success: Boolean = true
)

class AgentTool(
    val name: String,
    val title: String,
    val description: String,
    val parameters: JsonObject,
    val kind: AgentStepKind = AgentStepKind.TOOL,
    val execute: suspend (JsonObject) -> AgentToolResult
)

data class AgentModelRequest(val model: String, val messages: List<AgentModelMessage>, val tools: List<AgentTool>)

sealed interface AgentModelChunk {
    data class Text(val delta: String) : AgentModelChunk
    data class Tool(val index: Int, val id: String?, val name: String?, val arguments: String?) : AgentModelChunk
    data class Finished(val reason: String) : AgentModelChunk
}

fun interface AgentModelClient {
    fun stream(request: AgentModelRequest, apiKey: String): Flow<AgentModelChunk>
}
