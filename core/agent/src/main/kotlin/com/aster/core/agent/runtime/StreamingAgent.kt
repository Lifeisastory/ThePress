package com.aster.core.agent.runtime

import com.aster.core.agent.LlmException
import com.aster.core.agent.LlmFailure
import com.aster.core.agent.model.DigestGenerationException
import com.aster.core.agent.skills.AgentSkillStore
import com.aster.core.agent.skills.skillTool
import com.aster.core.analytics.*
import com.aster.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/** 顺序执行工具并把结果回传模型，调用方通过事件流维护展示和持久化 */
class StreamingAgent(
    private val client: AgentModelClient,
    private val skills: AgentSkillStore,
    logger: Logger = NoOpLogger,
    private val maxSteps: Int = 10
) {
    private val logs = RequestLogging(logger)

    fun run(
        model: String,
        system: String,
        history: List<AgentModelMessage>,
        tools: List<AgentTool>,
        apiKey: String
    ): Flow<AgentEvent> = flow {
        val catalog = skills.list()
        val registered = tools + skillTool(skills, catalog)
        require(registered.map { it.name }.distinct().size == registered.size)
        val metadata = JsonArray(catalog.map { buildJsonObject {
            put("name", it.name); put("description", it.description)
        } })
        val messages = mutableListOf(AgentModelMessage("system", """
            $system
            可用技能目录：$metadata
            任务匹配技能时先使用 use_skill 读取指令，随后按需读取引用资源并调用工具
            技能是操作指南，不会授予未注册的工具或脚本运行能力
        """.trimIndent())).apply { addAll(history) }
        val usedCallIds = mutableSetOf<String>()
        repeat(maxSteps) { index ->
            currentCoroutineContext().ensureActive()
            if (messages.sumOf { it.content.length + it.toolCalls.sumOf { call -> call.arguments.length } } > 120_000) {
                throw DigestGenerationException(DigestFailure.INPUT_TOO_LARGE)
            }
            val text = StringBuilder()
            val pending = sortedMapOf<Int, PendingCall>()
            var finish: String? = null
            logs.trace(origin = "NETWORK", operation = "AGENT_STEP", resource = "llm",
                request = { buildJsonObject { put("model", model); put("step", index + 1); put("messages", messages.size) } },
                describe = { LogResponse(LogOutcome.SUCCESS) }
            ) {
                client.stream(AgentModelRequest(model, messages.toList(), registered), apiKey).collect { chunk ->
                    when (chunk) {
                        is AgentModelChunk.Text -> {
                            text.append(chunk.delta)
                            if (text.length > 64_000) throw LlmException(LlmFailure.TRUNCATED)
                            emit(AgentEvent.TextDelta(chunk.delta))
                        }
                        is AgentModelChunk.Tool -> {
                            if (chunk.index !in 0..31) throw LlmException(LlmFailure.INVALID_RESPONSE)
                            pending.getOrPut(chunk.index) { PendingCall() }.append(chunk)
                        }
                        is AgentModelChunk.Finished -> finish = chunk.reason
                    }
                }
            }
            if (finish == "length") throw LlmException(LlmFailure.TRUNCATED)
            if (finish == "stop" && pending.isEmpty() && text.isNotBlank()) {
                emit(AgentEvent.Completed)
                return@flow
            }
            if (finish != "tool_calls" || pending.isEmpty()) throw LlmException(LlmFailure.INVALID_RESPONSE)
            val calls = pending.values.map { it.complete() }
            if (calls.any { !usedCallIds.add(it.id) }) throw LlmException(LlmFailure.INVALID_RESPONSE)
            messages += AgentModelMessage("assistant", text.toString(), calls)
            if (text.isNotBlank()) emit(AgentEvent.TextDelta("\n\n"))
            for (call in calls) {
                currentCoroutineContext().ensureActive()
                val tool = registered.find { it.name == call.name }
                emit(AgentEvent.StepStarted(AgentStep(call.id, call.name, tool?.title ?: "未知工具",
                    call.arguments, tool?.kind ?: AgentStepKind.TOOL)))
                val result = logs.trace(origin = "LOCAL_PROCESSING", operation = "AGENT_TOOL", resource = call.name,
                    describe = { result: AgentToolResult -> LogResponse(if (result.success) LogOutcome.SUCCESS else LogOutcome.FAILED) }
                ) {
                    try {
                        require(tool != null) { "工具不存在" }
                        val args = Json.parseToJsonElement(call.arguments).jsonObject
                        tool.execute(args).also { require(it.content.length <= 32_000) { "工具结果过大，请缩小查询范围" } }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        AgentToolResult("工具执行失败，请检查工具名称、参数或缩小查询范围，不要声称已经完成操作", success = false)
                    }
                }
                emit(AgentEvent.StepFinished(call.id, result.content, result.success))
                if (result.references.isNotEmpty()) emit(AgentEvent.References(result.references))
                messages += AgentModelMessage("tool", result.content, toolCallId = call.id)
            }
        }
        throw DigestGenerationException(DigestFailure.STEP_LIMIT)
    }

    private class PendingCall {
        var id: String? = null
        val name = StringBuilder()
        val arguments = StringBuilder()
        fun append(chunk: AgentModelChunk.Tool) {
            chunk.id?.takeIf { it.isNotBlank() }?.let {
                if (id != null && id != it) throw LlmException(LlmFailure.INVALID_RESPONSE)
                id = it
            }
            chunk.name?.let { name.append(it) }
            chunk.arguments?.let { arguments.append(it) }
            if (arguments.length > 32_000 || name.length > 128) throw LlmException(LlmFailure.INVALID_RESPONSE)
        }
        fun complete(): AgentToolCall {
            if (id.isNullOrBlank() || name.isBlank() || arguments.isBlank()) throw LlmException(LlmFailure.INVALID_RESPONSE)
            return AgentToolCall(id!!, name.toString(), arguments.toString())
        }
    }
}
