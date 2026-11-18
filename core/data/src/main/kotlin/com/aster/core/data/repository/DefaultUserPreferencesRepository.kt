package com.aster.core.data.repository

import com.aster.core.analytics.Logger
import com.aster.core.analytics.NoOpLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class DefaultUserPreferencesRepository(
    sourceOrder: Flow<List<String>>,
    private val persistSourceOrder: suspend (List<String>) -> Unit,
    logger: Logger = NoOpLogger
) : UserPreferencesRepository {
    private val requestLogging = DataRequestLogging(logger)
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

    private fun sourceOrderResponse(sourceIds: List<String>) = buildJsonObject {
        put("outcome", "SUCCESS")
        put("key", "news_source_order")
        put("count", sourceIds.size)
        put("sourceIds", JsonArray(sourceIds.map(::JsonPrimitive)))
    }
}
