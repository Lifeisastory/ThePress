package com.aster.core.agent

import kotlinx.serialization.json.JsonObject

data class LlmRequest(val model: String, val system: String, val user: String)
data class LlmResponse(val content: JsonObject, val usage: JsonObject)

fun interface LlmClient {
    suspend fun generate(request: LlmRequest, apiKey: String): LlmResponse
}

enum class LlmFailure { CONNECTION, TIMEOUT, HTTP, INVALID_RESPONSE, TRUNCATED }

class LlmException(val reason: LlmFailure, val httpCode: Int? = null) :
    Exception("LLM ${reason.name}${httpCode?.let { " ($it)" }.orEmpty()}")
