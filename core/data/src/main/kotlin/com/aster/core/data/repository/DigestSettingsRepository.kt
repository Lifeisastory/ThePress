package com.aster.core.data.repository

import com.aster.core.analytics.Logger
import com.aster.core.model.DigestSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

interface DigestSettingsRepository {
    val settings: Flow<DigestSettings>
    suspend fun save(settings: DigestSettings, apiKey: String? = null)
}

internal class DefaultDigestSettingsRepository(
    settingsFlow: Flow<DigestSettings>,
    private val persist: suspend (DigestSettings, String?) -> Unit,
    logger: Logger
) : DigestSettingsRepository {
    private val logs = DataRequestLogging(logger)
    override val settings = logs.observe(origin = "LOCAL_PREFERENCES", resource = "digest_settings",
        request = { buildJsonObject {} }, describe = { it.asLogResponse() }) { settingsFlow }.flowOn(Dispatchers.IO)

    override suspend fun save(settings: DigestSettings, apiKey: String?) = withContext(Dispatchers.IO) {
        require(settings.model in setOf("deepseek-flash", "deepseek-v4-pro"))
        require(apiKey == null || apiKey.trim().all { it.code in 33..126 })
        logs.trace(origin = "LOCAL_PREFERENCES", operation = "WRITE", resource = "digest_settings",
            request = { buildJsonObject {
                put("settings", settings.asLogResponse())
                put("keyAction", when { apiKey == null -> "KEEP"; apiKey.isBlank() -> "REMOVE"; else -> "REPLACE" })
            } }, describe = { buildJsonObject { put("outcome", "SUCCESS") } }) { persist(settings, apiKey) }
    }
}

private fun DigestSettings.asLogResponse() = buildJsonObject {
    put("outcome", "SUCCESS")
    put("sourceIds", JsonArray(sourceIds.sorted().map(::JsonPrimitive)))
    put("model", model)
    put("detail", detail.name)
    put("hasApiKey", hasApiKey)
}
