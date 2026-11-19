package com.aster.core.localdb.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aster.core.localdb.entity.NewsDigestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDigestDao {
    @Query("SELECT * FROM news_digest WHERE id = 1")
    fun observeLatest(): Flow<NewsDigestEntity?>

    @Upsert
    suspend fun save(entity: NewsDigestEntity)
}
