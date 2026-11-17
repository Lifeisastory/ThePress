package com.aster.core.analytics

import kotlinx.serialization.json.JsonElement
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEvent(
    val name: String,
    val category: String,
    val level: LogLevel = LogLevel.DEBUG,
    val attributes: Map<String, String> = emptyMap(),
    val cause: Throwable? = null,
    val response: JsonElement? = null
)
