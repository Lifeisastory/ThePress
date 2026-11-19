package com.aster.core.network

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * 创建共享 HTTP 客户端的 Retrofit 服务
 *
 * 建议在应用或数据容器中复用一个实例，并在初始化时创建各平台的 Service
 * 可以传入自定义 [httpClient] 配置超时、拦截器或 CookieJar
 */
class NetworkClient(
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
    val json: Json = Json {
        ignoreUnknownKeys = true
    }
) {
    /** [baseUrl] 必须以 / 结尾；String 响应按原始文本读取，DTO 使用 JSON 序列化 */
    fun <T : Any> createService(baseUrl: String, serviceClass: Class<T>): T =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            // 必须先匹配文本，否则 HTML/RSS 等 String 响应会被当作 JSON 解析
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(json.asConverterFactory("application/json; charset=utf-8".toMediaType()))
            .build()
            .create(serviceClass)

    inline fun <reified T : Any> createService(baseUrl: String): T =
        createService(baseUrl, T::class.java)
}
