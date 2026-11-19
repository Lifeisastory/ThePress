package com.aster.feature.digest.impl

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.aster.core.data.repository.DigestAgentRepository
import com.aster.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DigestAgentViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var saved = emptyList<AgentMessage>()
    private val conversations = mutableMapOf<Long, List<AgentMessage>>()
    private val summaries = MutableStateFlow<List<AgentConversationSummary>>(emptyList())
    private var calls = 0
    private var saveError = false
    private var loadError = false
    private var response: () -> Flow<AgentEvent> = { flowOf(AgentEvent.TextDelta("回答"), AgentEvent.Completed) }
    private val repository = object : DigestAgentRepository {
        override fun observeConversations() = summaries
        override suspend fun loadConversation(id: Long?): AgentConversation? {
            if (loadError) error("read")
            val selected = id ?: conversations.keys.maxOrNull() ?: 1L
            val messages = conversations[selected] ?: saved
            return messages.takeIf { it.isNotEmpty() }?.let { AgentConversation(selected, it) }
        }
        override suspend fun saveConversation(id: Long?, messages: List<AgentMessage>): Long {
            if (saveError) error("save")
            saved = messages
            val key = id ?: ((conversations.keys.maxOrNull() ?: 0L) + 1)
            conversations[key] = messages
            summaries.value = conversations.map { (key, value) -> AgentConversationSummary(key, value.first().text, key, value.size) }
            return key
        }
        override fun reply(prompt: String, history: List<AgentMessage>): Flow<AgentEvent> { calls++; return response() }
    }
    private fun create() = ViewModelProvider(store, DigestAgentViewModel.factory(repository))[DigestAgentViewModel::class.java]
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun openingDoesNotCallModelAndEventsUpdateAndPersistTheSameReply() = runTest(dispatcher) {
        response = { flow {
            emit(AgentEvent.StepStarted(AgentStep("skill", "use_skill", "加载技能", "{}", AgentStepKind.SKILL)))
            emit(AgentEvent.StepFinished("skill", "instructions", true))
            emit(AgentEvent.TextDelta("第一段")); emit(AgentEvent.TextDelta("第二段")); emit(AgentEvent.Completed)
        } }
        val vm = create(); runCurrent()
        assertEquals(0, calls)
        assertTrue(vm.send("简报")); runCurrent()
        assertEquals("第一段第二段", vm.uiState.value.messages.last().text)
        assertEquals(AgentStatus.COMPLETED, saved.last().status)
        assertEquals(AgentStatus.COMPLETED, saved.last().steps.single().status)
        assertEquals(vm.uiState.value.messages, saved)
    }

    @Test fun cancellationPreservesPartialTextMarksToolsAndAllowsRetryWithoutDuplicatingQuestion() = runTest(dispatcher) {
        response = { flow {
            emit(AgentEvent.TextDelta("partial"))
            emit(AgentEvent.StepStarted(AgentStep("tool", "read_news", "读取", "{}")))
            awaitCancellation()
        } }
        val vm = create(); runCurrent()
        vm.send("question"); runCurrent()
        assertFalse(vm.send("duplicate"))
        vm.cancel(); runCurrent()
        assertEquals(AgentStatus.CANCELLED, saved.last().status)
        assertEquals("partial", saved.last().text)
        assertEquals(AgentStatus.CANCELLED, saved.last().steps.single().status)
        assertFalse(vm.uiState.value.running)
        response = { flowOf(AgentEvent.TextDelta("retry"), AgentEvent.Completed) }
        vm.retry(); runCurrent()
        assertEquals(2, saved.size)
        assertEquals("question", saved.first().text)
        assertEquals("retry", saved.last().text)
        assertEquals(2, calls)
    }

    @Test fun immediateCancellationStillFinishesTheRun() = runTest(dispatcher) {
        response = { flow { awaitCancellation() } }
        val vm = create(); runCurrent()
        vm.send("question"); vm.cancel(); runCurrent()
        assertFalse(vm.uiState.value.running)
        assertEquals(AgentStatus.CANCELLED, saved.last().status)
    }

    @Test fun persistenceFailurePreventsNetworkAndCanBeRetried() = runTest(dispatcher) {
        val vm = create(); runCurrent(); saveError = true
        vm.send("question"); runCurrent()
        assertEquals(0, calls)
        assertTrue(vm.uiState.value.saveFailed)
        assertEquals(DigestFailure.STORAGE, vm.uiState.value.messages.last().failure)
        saveError = false
        vm.saveAgain(); runCurrent()
        assertFalse(vm.uiState.value.saveFailed)
        vm.retry(); runCurrent()
        assertEquals(1, calls)
    }

    @Test fun failedLoadCannotOverwriteStoredConversation() = runTest(dispatcher) {
        saved = listOf(AgentMessage("old", AgentRole.USER, "old question"))
        loadError = true
        val vm = create(); runCurrent()
        assertTrue(vm.uiState.value.readFailed)
        assertFalse(vm.send("new"))
        vm.newConversation(); runCurrent()
        assertEquals("old", saved.single().id)
        loadError = false
        vm.load(); runCurrent()
        assertEquals(saved, vm.uiState.value.messages)
    }

    @Test fun missingCompletionIsAVisibleFailureRatherThanASuccess() = runTest(dispatcher) {
        response = { flowOf(AgentEvent.TextDelta("partial")) }
        val vm = create(); runCurrent()
        vm.send("question"); runCurrent()
        assertEquals(AgentStatus.FAILED, saved.last().status)
        assertEquals(DigestFailure.INVALID_RESPONSE, saved.last().failure)
    }

    @Test fun newConversationRetainsHistoryAndSwitchingRestoresItsMessages() = runTest(dispatcher) {
        val vm = create(); runCurrent()
        vm.send("第一段会话"); runCurrent()
        val firstId = vm.uiState.value.conversationId!!
        vm.newConversation(); runCurrent()
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertEquals(1, vm.uiState.value.conversations.size)
        vm.send("第二段会话"); runCurrent()
        assertNotEquals(firstId, vm.uiState.value.conversationId)
        assertEquals(2, vm.uiState.value.conversations.size)
        vm.load(firstId); runCurrent()
        assertEquals("第一段会话", vm.uiState.value.messages.first().text)
        assertEquals(2, calls)
    }

    @Test fun cannotSwitchConversationOrCreateNewWhileReplyIsRunning() = runTest(dispatcher) {
        val vm = create(); runCurrent()
        vm.send("first"); runCurrent()
        val id = vm.uiState.value.conversationId
        response = { flow { awaitCancellation() } }
        vm.send("next"); runCurrent()
        vm.newConversation(); vm.load(999); runCurrent()
        assertEquals(id, vm.uiState.value.conversationId)
        assertTrue(vm.uiState.value.running)
        vm.cancel(); runCurrent()
    }
}
