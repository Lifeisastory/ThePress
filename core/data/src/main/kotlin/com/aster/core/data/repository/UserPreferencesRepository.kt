package com.aster.core.data.repository

import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val sourceOrder: Flow<List<String>>

    suspend fun saveSourceOrder(sourceIds: List<String>)
}
