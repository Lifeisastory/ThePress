package com.aster.core.agent.model

import com.aster.core.model.DigestFailure
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DigestGenerationException(
    val reason: DigestFailure,
    val diagnostics: JsonObject = buildJsonObject { put("reason", reason.name) }
) : Exception(reason.name)
