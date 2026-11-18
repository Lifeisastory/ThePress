package com.aster.core.analytics

import java.net.URI
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object LogFormatter {
    private val json = Json { prettyPrint = true }
    private val sensitiveNames = listOf("authorization", "cookie", "token", "password", "secret", "apikey")

    fun format(event: LogEvent): String = buildString {
        append("[${escape(event.category)}] ${escape(event.name)}")
        event.attributes.forEach { (key, value) ->
            val normalized = key.lowercase().replace("_", "").replace("-", "")
            val safeValue = when {
                sensitiveNames.any { it in normalized } -> "[REDACTED]"
                normalized.endsWith("url") -> sanitizeUrl(value)
                else -> value
            }
            append(" ${escape(key)}=\"${escape(safeValue)}\"")
        }
        event.request?.let {
            append("\nrequest=\n")
            append(json.encodeToString(JsonElement.serializer(), sanitizePayload(it)))
        }
        event.response?.let {
            append("\nresponse=\n")
            append(json.encodeToString(JsonElement.serializer(), sanitizePayload(it)))
        }
        // 不输出异常 message：其中可能带完整 URL、请求凭据或上游正文
        // 保留异常类型、调用栈和 cause 链，包括 Android Log 通常省略的 DNS 异常
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var cause = event.cause
        while (cause != null && seen.add(cause)) {
            append("\n${cause.javaClass.name}")
            cause.stackTrace.forEach { append("\n\tat $it") }
            cause = cause.cause
        }
    }

    private fun sanitizePayload(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { (key, child) ->
            val normalized = key.lowercase().replace("_", "").replace("-", "")
            when {
                sensitiveNames.any { it in normalized } -> JsonPrimitive("[REDACTED]")
                normalized.endsWith("url") && child is JsonPrimitive && child.isString ->
                    JsonPrimitive(sanitizeUrl(child.content))
                else -> sanitizePayload(child)
            }
        })
        is JsonArray -> JsonArray(value.map(::sanitizePayload))
        else -> value
    }

    private fun sanitizeUrl(value: String): String = try {
        val uri = URI(value)
        if (uri.scheme !in listOf("http", "https") || uri.host == null) {
            "[INVALID_URL]"
        } else {
            URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toASCIIString()
        }
    } catch (_: Exception) {
        "[INVALID_URL]"
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\")
        .replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
}
