# network

基础 HTTP 请求模块，提供共享 OkHttp 客户端、Retrofit Service 创建和协程结果封装。

## 创建服务

在应用或数据容器中复用一个 `NetworkClient`，每个 Service 创建一次。不同 baseUrl 的服务共享该实例的 OkHttp 连接池。

```kotlin
import com.aster.core.network.NetworkClient
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

@Serializable
data class ArticleDto(val id: String, val title: String)

interface ArticleApi {
    @GET("articles")
    suspend fun articles(@Query("page") page: Int): List<ArticleDto>

    @POST("articles")
    suspend fun create(@Body article: ArticleDto): ArticleDto

    @GET
    suspend fun text(
        @Url url: String,
        @Header("Referer") referer: String? = null
    ): String
}

val networkClient = NetworkClient()
val articleApi = networkClient.createService<ArticleApi>("https://example.com/api/")
```

- baseUrl 必须以 `/` 结尾，使用 Retrofit 原生的路径拼接规则。
- `@Serializable` DTO 使用 Kotlin Serialization；默认忽略新增 JSON 字段，缺少必填字段仍会报错。
- `String` 使用 Scalars converter，按原始文本读取 HTML、XML 等内容；这里不会解析 DOM 或 Feed。
- Scalars converter 优先，因此 `@Body String` 是文本请求体。发送 JSON 时使用 DTO 或 `JsonElement`。
- 无响应正文的方法声明为 `Unit`，例如 `suspend fun delete()`；需要响应码和响应头时声明为 `retrofit2.Response<T>`。
- 如果 DTO 定义在其他模块，该模块也要应用 `alias(libs.plugins.kotlin.serialization)`。当前 network 已应用该插件。

## 执行请求

```kotlin
import com.aster.core.network.NetworkResult
import com.aster.core.network.networkRequest

suspend fun loadArticles() {
    when (val result = networkRequest { articleApi.articles(page = 1) }) {
        is NetworkResult.Success -> {
            val articles = result.data
            // 交给数据层处理
        }
        is NetworkResult.Failure -> {
            val error = result.error
            // 由调用方决定展示、重试或缓存策略
        }
    }
}
```

`networkRequest` 在 IO 调度器中调用 suspend Service，并区分以下错误：

| 类型 | 数据 |
| --- | --- |
| `NetworkError.Http` | 非 2xx 状态码、状态消息、响应头和可选错误正文 |
| `NetworkError.Timeout` | 调用或读写超时产生的 `InterruptedIOException` |
| `NetworkError.Connection` | 其他 I/O 错误，如 DNS、连接或 TLS 失败 |
| `NetworkError.Serialization` | JSON 序列化或反序列化失败 |

协程取消会传播并取消 Retrofit 底层请求；程序错误继续抛出。请求 block 只放网络调用，业务处理放在结果之后。

若 Service 返回 `Response<T>`，封装同样检查 HTTP 状态；成功时 `result.data` 仍是完整的 `Response<T>`。如果自行使用 `ResponseBody`/流式响应，消费和关闭资源由调用方负责。

## 自定义配置

默认连接超时 10 秒、读写超时 20 秒、整次调用超时 30 秒。可注入自定义 OkHttpClient 和 Json：

```kotlin
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

val customClient = NetworkClient(
    httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
)
```

模块沿用 OkHttp 默认连接恢复行为，不添加业务层重试。请求参数、请求头、表单及其他 HTTP 方法通过 Retrofit 注解定义。缓存、鉴权和各平台抓取规则由后续具体需求接入。

## 验证

```powershell
.\gradlew.bat :core:network:assembleDebug :core:network:testDebugUnitTest :core:network:lintDebug
```

单元测试通过本地 MockWebServer 验证 GET/POST、JSON、文本及字符集、多 baseUrl、HTTP 错误、超时和取消，不依赖外部平台。
