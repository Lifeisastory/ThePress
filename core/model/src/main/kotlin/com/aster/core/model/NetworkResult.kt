package com.aster.core.model

/** 预期的网络结果；协程取消和程序错误仍通过异常传播 */
sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>

    data class Failure(val error: NetworkError) : NetworkResult<Nothing>
}
