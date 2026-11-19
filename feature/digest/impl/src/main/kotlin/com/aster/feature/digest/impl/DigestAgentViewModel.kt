package com.aster.feature.digest.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aster.core.data.repository.DigestAgentRepository
import com.aster.core.model.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.retryWhen

internal data class DigestAgentState(
    val conversationId: Long? = null,
    val conversations: List<AgentConversationSummary> = emptyList(),
    val historyReadFailed: Boolean = false,
    val messages: List<AgentMessage> = emptyList(),
    val loading: Boolean = true,
    val readFailed: Boolean = false,
    val saveFailed: Boolean = false,
    val running: Boolean = false
)

internal class DigestAgentViewModel(private val repository: DigestAgentRepository) : ViewModel() {
    private val state = MutableStateFlow(DigestAgentState())
    val uiState = state.asStateFlow()
    private var generation: Job? = null
    private var loading: Job? = null

    init {
        load()
        viewModelScope.launch {
            repository.observeConversations().retryWhen { _, _ ->
                state.update { it.copy(historyReadFailed = true) }; delay(3000); true
            }.collect { rows -> state.update { it.copy(conversations = rows, historyReadFailed = false) } }
        }
    }

    fun load(id: Long? = state.value.conversationId) {
        if (state.value.running || loading?.isActive == true || state.value.saveFailed) return
        state.update { it.copy(loading = true) }
        loading = viewModelScope.launch {
            try {
                val conversation = repository.loadConversation(id)
                state.update { it.copy(conversationId = conversation?.id, messages = conversation?.messages.orEmpty(), readFailed = false) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { state.update { it.copy(readFailed = true) }
            } finally { state.update { it.copy(loading = false) } }
        }
    }

    fun send(prompt: String): Boolean = startReply(prompt, state.value.messages)

    fun retry() {
        val messages = state.value.messages
        if (messages.size < 2 || messages.last().status !in setOf(AgentStatus.FAILED, AgentStatus.CANCELLED)) return
        startReply(messages[messages.lastIndex - 1].text, messages.dropLast(2))
    }

    private fun startReply(prompt: String, history: List<AgentMessage>): Boolean {
        val text = prompt.trim()
        if (state.value.running || state.value.loading || state.value.readFailed || text.isEmpty() || text.length > 4000) return false
        val id = UUID.randomUUID().toString()
        state.update { it.copy(running = true, saveFailed = false, messages = history + listOf(
            AgentMessage(UUID.randomUUID().toString(), AgentRole.USER, text),
            AgentMessage(id, AgentRole.ASSISTANT, status = AgentStatus.RUNNING)
        )) }
        generation = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                persist()
                var terminal = false
                repository.reply(text, history).collect { event ->
                    check(!terminal) { "运行结束后不能继续发送事件" }
                    updateMessage(id) { message -> when (event) {
                        is AgentEvent.TextDelta -> message.copy(text = message.text + event.text)
                        is AgentEvent.StepStarted -> message.copy(steps = message.steps + event.step)
                        is AgentEvent.StepFinished -> message.copy(steps = message.steps.map { step ->
                            if (step.id == event.id) step.copy(output = event.output,
                                status = if (event.success) AgentStatus.COMPLETED else AgentStatus.FAILED) else step
                        })
                        is AgentEvent.References -> message.copy(references = (message.references + event.references)
                            .distinctBy { it.sourceId to it.articleId })
                        AgentEvent.Completed -> { terminal = true; message.copy(status = AgentStatus.COMPLETED) }
                        is AgentEvent.Failed -> { terminal = true; message.finished(AgentStatus.FAILED, event.reason) }
                    } }
                }
                if (!terminal) updateMessage(id) { it.finished(AgentStatus.FAILED, DigestFailure.INVALID_RESPONSE) }
            } catch (error: CancellationException) {
                updateMessage(id) { it.finished(AgentStatus.CANCELLED) }
                throw error
            } catch (_: Exception) {
                updateMessage(id) { it.finished(AgentStatus.FAILED, DigestFailure.STORAGE) }
            } finally {
                // 完成保存之前保持 running，避免新一轮覆盖尚未落盘的会话
                withContext(NonCancellable) {
                    try {
                        withTimeout(5000) { persist() }
                    } catch (_: Exception) { state.update { it.copy(saveFailed = true) } }
                }
                state.update { it.copy(running = false) }
            }
        }
        return true
    }

    fun cancel() { generation?.cancel() }

    fun newConversation() {
        if (state.value.running || state.value.loading || state.value.readFailed || state.value.saveFailed) return
        state.update { it.copy(conversationId = null, messages = emptyList()) }
    }

    fun saveAgain() {
        if (state.value.running || state.value.loading) return
        state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                persist()
                state.update { it.copy(saveFailed = false) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { state.update { it.copy(saveFailed = true) }
            } finally { state.update { it.copy(loading = false) } }
        }
    }

    private fun updateMessage(id: String, transform: (AgentMessage) -> AgentMessage) {
        state.update { it.copy(messages = it.messages.map { message -> if (message.id == id) transform(message) else message }) }
    }

    private suspend fun persist() {
        val current = state.value
        val id = repository.saveConversation(current.conversationId, current.messages)
        state.update { it.copy(conversationId = id) }
    }

    private fun AgentMessage.finished(status: AgentStatus, failure: DigestFailure? = null) = copy(
        status = status, failure = failure, steps = steps.map {
            if (it.status == AgentStatus.RUNNING) it.copy(status = if (status == AgentStatus.CANCELLED) AgentStatus.CANCELLED else AgentStatus.FAILED) else it
        }
    )

    companion object {
        fun factory(repository: DigestAgentRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(DigestAgentViewModel(repository))!!
        }
    }
}
