package com.aster.core.network

import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.serialization.SerializationException
import okhttp3.Headers

/** 预期的网络结果；协程取消和程序错误仍通过异常传播 */
sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>

    data class Failure(val error: NetworkError) : NetworkResult<Nothing>
}

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
}
