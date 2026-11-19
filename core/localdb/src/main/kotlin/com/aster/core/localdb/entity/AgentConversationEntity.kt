package com.aster.core.localdb.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

/** 每行保存一段独立会话，不包含模型凭据 */
@Entity(tableName = "agent_conversation")
data class AgentConversationEntity(
    @PrimaryKey val id: Long = 1,
    val payload: String,
    @ColumnInfo(defaultValue = "''") val title: String = "",
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val messageCount: Int = 0
)
