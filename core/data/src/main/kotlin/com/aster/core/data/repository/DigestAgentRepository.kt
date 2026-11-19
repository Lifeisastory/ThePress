package com.aster.core.data.repository

import com.aster.core.model.AgentEvent
import com.aster.core.model.AgentMessage
import com.aster.core.model.AgentConversation
import com.aster.core.model.AgentConversationSummary
import kotlinx.coroutines.flow.Flow

interface DigestAgentRepository {
    fun observeConversations(): Flow<List<AgentConversationSummary>>
    suspend fun loadConversation(id: Long? = null): AgentConversation?
    suspend fun saveConversation(id: Long?, messages: List<AgentMessage>): Long
    fun reply(prompt: String, history: List<AgentMessage>): Flow<AgentEvent>
}
