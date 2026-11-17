package com.aster.core.network

import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.Headers.Companion.headersOf
import retrofit2.HttpException
import retrofit2.Response

/**
 * 在 IO 调度器执行一次 suspend 请求，并转换预期的网络异常
 *
 * Service 可直接返回 DTO、String、Unit，或返回 Response<T> 以保留响应元信息
 * Response<T> 也会检查 HTTP 状态，但成功时不拆包
 * [block] 仅放请求调用；取消和程序错误继续抛出，不转换为普通失败
 */
suspend fun <T> networkRequest(block: suspend () -> T): NetworkResult<T> =
    withContext(Dispatchers.IO) {
        try {
            val data = block()
            if (data is Response<*> && !data.isSuccessful) {
                throw HttpException(data)
            }
            NetworkResult.Success(data)
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            val response = error.response()
            val body = try {
                response?.errorBody()?.use { it.string() }
            } catch (_: IOException) {
                // 错误正文读取失败时，仍保留已经收到的 HTTP 状态和响应头
                null
            }
            NetworkResult.Failure(
                NetworkError.Http(
                    code = error.code(),
                    message = error.message(),
                    headers = response?.headers() ?: headersOf(),
                    body = body
                )
            )
        } catch (error: SerializationException) {
            NetworkResult.Failure(NetworkError.Serialization(error))
        } catch (error: InterruptedIOException) {
            NetworkResult.Failure(NetworkError.Timeout(error))
        } catch (error: IOException) {
            NetworkResult.Failure(NetworkError.Connection(error))
        }
    }
