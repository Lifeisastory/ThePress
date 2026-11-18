package com.aster.core.localdb.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 保存最近一次完整简报及来源快照，清理新闻缓存不会删除简报 */
@Entity(tableName = "news_digest")
data class NewsDigestEntity(@PrimaryKey val id: Int = 1, val payload: String)
