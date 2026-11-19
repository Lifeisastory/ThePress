package com.aster.core.localdb.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import com.aster.core.localdb.entity.AgentConversationEntity
import com.aster.core.localdb.dto.AgentConversationSummaryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentConversationDao {
    @Query("SELECT * FROM agent_conversation WHERE id = :id")
    suspend fun read(id: Long = 1): AgentConversationEntity?

    @Query("SELECT * FROM agent_conversation WHERE payload != '[]' ORDER BY updatedAt DESC, id DESC LIMIT 1")
    suspend fun readLatest(): AgentConversationEntity?

    @Query("SELECT id, title, updatedAt, messageCount FROM agent_conversation WHERE payload != '[]' ORDER BY updatedAt DESC, id DESC")
    fun observeSummaries(): Flow<List<AgentConversationSummaryRow>>

    @Query("SELECT COALESCE(MAX(id), 0) + 1 FROM agent_conversation")
    suspend fun nextId(): Long

    @Upsert
    suspend fun save(entity: AgentConversationEntity)

    @Transaction
    suspend fun saveNew(payload: String, title: String, updatedAt: Long, messageCount: Int): Long {
        val id = nextId()
        save(AgentConversationEntity(id, payload, title, updatedAt, messageCount))
        return id
    }
}
