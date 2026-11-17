package com.aster.thepress.logging

import android.util.Log
import com.aster.core.network.model.NetworkError

/** 仅由 Debug 应用调用；不输出请求头、Cookie、请求体或响应正文 */
internal fun logSourceFailure(sourceId: String, error: NetworkError) {
    val cause = when (error) {
        is NetworkError.Connection -> error.cause
        is NetworkError.Timeout -> error.cause
        else -> null
    }
    val detail = when (error) {
        // Android 的 Log 会省略 UnknownHostException 的堆栈，消息中也保留异常类型及原因
        is NetworkError.Connection, is NetworkError.Timeout ->
            "${cause?.javaClass?.simpleName}: ${cause?.message}"
        is NetworkError.Http -> "HTTP ${error.code}"
        is NetworkError.Processing -> "${error.stage}: ${error.message}"
        is NetworkError.Serialization -> "Serialization: ${error.cause.javaClass.simpleName}"
    }
    Log.e("ThePressNetwork", "source=$sourceId $detail", cause)
}
