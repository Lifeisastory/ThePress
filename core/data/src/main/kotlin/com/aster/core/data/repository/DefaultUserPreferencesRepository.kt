package com.aster.core.data.repository

import com.aster.core.analytics.Logger
import com.aster.core.analytics.LogOutcome
import com.aster.core.analytics.LogResponse
import com.aster.core.analytics.NoOpLogger
import com.aster.core.analytics.RequestLogging
import com.aster.core.model.AppearanceSettings
import com.aster.core.model.ThemeMode
import com.aster.core.model.ThemeColor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class DefaultUserPreferencesRepository(
    sourceOrder: Flow<List<String>>,
    private val persistSourceOrder: suspend (List<String>) -> Unit,
    appearance: Flow<AppearanceSettings>,
    private val persistThemeMode: suspend (ThemeMode) -> Unit,
    private val persistThemeColor: suspend (ThemeColor) -> Unit,
    logger: Logger = NoOpLogger
) : UserPreferencesRepository {
    private val requestLogging = RequestLogging(logger)
    override val appearance = requestLogging.observe(
        origin = "LOCAL_PREFERENCES",
        resource = "user_preferences",
        request = { buildJsonObject { put("key", "appearance") } },
        describe = ::appearanceResponse
    ) { appearance }

    override suspend fun saveThemeMode(mode: ThemeMode) = saveAppearance("theme_mode", mode.name) {
        persistThemeMode(mode)
    }

    override suspend fun saveThemeColor(color: ThemeColor) = saveAppearance("theme_color", color.name) {
        persistThemeColor(color)
    }

    private suspend fun saveAppearance(key: String, value: String, persist: suspend () -> Unit) {
        requestLogging.trace(
            origin = "LOCAL_PREFERENCES", operation = "WRITE", resource = "user_preferences",
            request = { buildJsonObject { put("key", key); put("value", value) } },
            describe = { LogResponse(LogOutcome.SUCCESS) },
            block = persist
        )
    }
    override val sourceOrder = requestLogging.observe(
        origin = "LOCAL_PREFERENCES",
        resource = "user_preferences",
        request = { buildJsonObject { put("key", "news_source_order") } },
        describe = { ids: List<String> -> sourceOrderResponse(ids) }
    ) { sourceOrder }

    override suspend fun saveSourceOrder(sourceIds: List<String>) {
        val normalized = sourceIds.filter { it.isNotBlank() }.distinct()
        requestLogging.trace(
            origin = "LOCAL_PREFERENCES",
            operation = "WRITE",
            resource = "user_preferences",
            request = { buildJsonObject {
                put("key", "news_source_order")
                put("sourceIds", JsonArray(normalized.map(::JsonPrimitive)))
            } },
            describe = { sourceOrderResponse(normalized) }
        ) {
            persistSourceOrder(normalized)
        }
    }

    private fun appearanceResponse(settings: AppearanceSettings) = LogResponse(LogOutcome.SUCCESS, buildJsonObject {
        put("mode", settings.mode.name)
        put("color", settings.color.name)
    })

    private fun sourceOrderResponse(sourceIds: List<String>) = LogResponse(LogOutcome.SUCCESS, buildJsonObject {
        put("key", "news_source_order")
        put("count", sourceIds.size)
        put("sourceIds", JsonArray(sourceIds.map(::JsonPrimitive)))
    })
}
