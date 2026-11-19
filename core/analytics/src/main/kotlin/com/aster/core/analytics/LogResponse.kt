package com.aster.core.analytics

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class LogOutcome {
    SUCCESS, FAILED, CANCELED, COMPLETED, HIT, MISS, EXPIRED, PARTIAL, UNKNOWN
}

/** 显式声明日志结果；业务字段无需包含 outcome，输出时以此处的状态为准 */
data class LogResponse(
    val outcome: LogOutcome,
    val payload: JsonObject = JsonObject(emptyMap())
) {
    fun toJsonObject(): JsonObject = buildJsonObject {
        put("outcome", outcome.name)
        payload.forEach { (key, value) -> if (key != "outcome") put(key, value) }
    }
}
