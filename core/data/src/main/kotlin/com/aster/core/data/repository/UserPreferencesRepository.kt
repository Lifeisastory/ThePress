package com.aster.core.data.repository

import kotlinx.coroutines.flow.Flow
import com.aster.core.model.AppearanceSettings
import com.aster.core.model.ThemeMode
import com.aster.core.model.ThemeColor

interface UserPreferencesRepository {
    val appearance: Flow<AppearanceSettings>

    suspend fun saveThemeMode(mode: ThemeMode)

    suspend fun saveThemeColor(color: ThemeColor)

    val sourceOrder: Flow<List<String>>

    suspend fun saveSourceOrder(sourceIds: List<String>)
}
