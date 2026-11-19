package com.aster.core.model

import kotlinx.serialization.Serializable

data class AgentConversationSummary(val id: Long, val title: String, val updatedAt: Long, val messageCount: Int)

data class AgentConversation(val id: Long, val messages: List<AgentMessage>)

@Serializable
enum class AgentRole { USER, ASSISTANT }

@Serializable
enum class AgentStatus { RUNNING, COMPLETED, FAILED, CANCELLED }

@Serializable
enum class AgentStepKind { TOOL, SKILL }

@Serializable
data class AgentStep(
    val id: String,
    val name: String,
    val title: String,
    val input: String,
    val kind: AgentStepKind = AgentStepKind.TOOL,
    val status: AgentStatus = AgentStatus.RUNNING,
    val output: String = ""
)

@Serializable
data class AgentMessage(
    val id: String,
    val role: AgentRole,
    val text: String = "",
    val steps: List<AgentStep> = emptyList(),
    val references: List<DigestReference> = emptyList(),
    val status: AgentStatus = AgentStatus.COMPLETED,
    val failure: DigestFailure? = null
)

/** 运行事件不携带凭据，UI 和存储使用同一组消息契约 */
sealed interface AgentEvent {
    data class TextDelta(val text: String) : AgentEvent
    data class StepStarted(val step: AgentStep) : AgentEvent
    data class StepFinished(val id: String, val output: String, val success: Boolean) : AgentEvent
    data class References(val references: List<DigestReference>) : AgentEvent
    data object Completed : AgentEvent
    data class Failed(val reason: DigestFailure) : AgentEvent
}
