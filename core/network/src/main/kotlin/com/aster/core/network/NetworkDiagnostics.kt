package com.aster.core.network

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.LogLevel
import com.aster.core.analytics.Logger
import com.aster.core.network.model.NetworkError

/** 网络错误转换为通用日志事件，analytics 不依赖网络 DTO */
fun Logger.logSourceFailure(sourceId: String, error: NetworkError) {
    val cause = when (error) {
        is NetworkError.Connection -> error.cause
        is NetworkError.Timeout -> error.cause
        is NetworkError.Serialization -> error.cause
        is NetworkError.Processing -> error.cause
        is NetworkError.Http -> null
    }
    log(LogEvent(
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
    ))
}
