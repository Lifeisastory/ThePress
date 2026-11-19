package com.aster.core.data.repository

import com.aster.core.model.DigestSettings
import kotlinx.coroutines.flow.Flow

interface DigestSettingsRepository {
    val settings: Flow<DigestSettings>
    suspend fun save(settings: DigestSettings, apiKey: String? = null)
}

