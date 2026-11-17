package com.aster.core.network.model

import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.serialization.SerializationException
import okhttp3.Headers

sealed interface NetworkError {
    /** 保留响应头，调用方可读取 Retry-After 等信息；错误响应体已读取并关闭 */
    data class Http(
        val code: Int,
        val message: String,
        val headers: Headers,
        val body: String?
    ) : NetworkError

    data class Timeout(val cause: InterruptedIOException) : NetworkError

    data class Connection(val cause: IOException) : NetworkError

    data class Serialization(val cause: SerializationException) : NetworkError

    data class Processing(
        val sourceId: String,
        val stage: ProcessingStage,
        val message: String,
        val cause: Throwable? = null
    ) : NetworkError
}
