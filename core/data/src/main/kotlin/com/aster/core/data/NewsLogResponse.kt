package com.aster.core.data

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.LogLevel
import com.aster.core.analytics.LogOutcome
import com.aster.core.analytics.LogResponse
import com.aster.core.analytics.Logger
import com.aster.core.analytics.log
import com.aster.core.data.model.toArticle
import com.aster.core.localdb.entity.NewsCache
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsRefreshResult
import com.aster.core.model.NetworkError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun List<NewsArticle>.asLogResponse(outcome: LogOutcome = LogOutcome.SUCCESS): LogResponse = LogResponse(outcome, buildJsonObject {
    put("count", size)
    put("articles", JsonArray(map { article -> buildJsonObject {
        put("sourceId", article.sourceId)
        put("id", article.id)
        put("title", article.title)
        put("url", article.url)
        put("summary", article.summary?.let(::JsonPrimitive) ?: JsonNull)
        put("imageUrl", article.imageUrl?.let(::JsonPrimitive) ?: JsonNull)
        put("stockInfo", article.stockInfo?.let { stock -> buildJsonObject {
            put("code", stock.code)
            put("price", stock.price)
            put("change", stock.change)
            put("changePercent", stock.changePercent)
        } } ?: JsonNull)
    } }))
})

internal fun NewsRefreshResult.asLogResponse(): LogResponse = when (this) {
    NewsRefreshResult.Success -> LogResponse(LogOutcome.SUCCESS)
    is NewsRefreshResult.Failure -> LogResponse(LogOutcome.FAILED, buildJsonObject {
        put("reason", reason.name)
        put("httpCode", httpCode?.let(::JsonPrimitive) ?: JsonNull)
    })
}

internal fun NewsCache?.asLogResponse(fresh: Boolean): LogResponse = if (this == null) {
    LogResponse(LogOutcome.MISS)
} else {
    val articles = articles.map { it.toArticle() }
    val response = articles.asLogResponse(if (fresh) LogOutcome.HIT else LogOutcome.EXPIRED)
    response.copy(payload = JsonObject(response.payload + ("fetchedAt" to JsonPrimitive(metadata.fetchedAt))))
}

/** 仓库将来源加载失败转换为日志事件，通用输出交给 analytics */
internal fun Logger.logSourceFailure(sourceId: String, error: NetworkError) = log {
    val cause = when (error) {
        is NetworkError.Connection -> error.cause
        is NetworkError.Timeout -> error.cause
        is NetworkError.Serialization -> error.cause
        is NetworkError.Processing -> error.cause
        is NetworkError.Http -> null
    }
    LogEvent(
        name = "source_failed",
        category = "network",
        level = LogLevel.ERROR,
        attributes = buildMap {
            put("sourceId", sourceId)
            put("errorType", error.javaClass.simpleName)
            if (error is NetworkError.Http) put("status", error.code.toString())
            if (error is NetworkError.Processing) put("stage", error.stage.name)
        },
        cause = cause
    )
}
