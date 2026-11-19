package com.aster.core.agent.model

import kotlinx.serialization.json.JsonObject

internal data class DigestValidation(
    val items: List<JsonObject>,
    val missingIds: Set<String>,
    val diagnostics: JsonObject
)
