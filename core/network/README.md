# network

网络请求与来源适配模块，提供共享 OkHttp 客户端、Retrofit Service、协程结果封装，以及按来源配置选择的请求、提取和映射工具。

## 按来源获取列表

在应用或数据容器中复用 `NetworkClient` 和 `AdapterManager`，在协程中传入已注册的来源 ID：

```kotlin
import com.aster.core.network.NetworkClient
import com.aster.core.network.adapters.AdapterManager
import com.aster.core.network.model.NetworkResult
import com.aster.core.network.sources.BuiltInSources

val networkClient = NetworkClient()
val adapters = AdapterManager(networkClient, BuiltInSources.all)

suspend fun loadBaidu() {
    when (val result = adapters.fetch("baidu")) {
        is NetworkResult.Success -> {
            val items = result.data // List<SourceItem>
            // 交给 core:data 整合、缓存或持久化
        }
        is NetworkResult.Failure -> {
            val error = result.error
            // 由调用方决定展示或重试
        }
    }
}
```

| 来源 ID | 来源 | 请求与处理 |
| --- | --- | --- |
| `qqvideo-tv-hotsearch` | 腾讯视频电视剧热搜榜 | HTTP POST → JSON → 字段映射 |
| `baidu` | 百度热搜 | HTTP GET → 页面内嵌 JSON → 字段映射 |

`SourceItem` 包含 `sourceId`、`id`、`title`、`url` 和可空的 `summary`。结果保留来源顺序，同一来源按 ID 去重；不生成来源没有提供的发布时间。

调用方不必传入“HTTP 接口”“内嵌 JSON”等文字：这些选择已经写入 `SourceDefinition`。显示名称与稳定 ID 分离，同一平台有多个榜单时可以注册多个 ID。两个平台共用工具，不存在平台专用适配器类。

### 工具结构

```text
adapters/
├── AdapterManager.kt       来源查找与调用流程
├── AdapterException.kt     工具处理异常
├── request/                动态 HTTP 请求构建与执行
├── extract/                JSON、带标记的内嵌 JSON 提取
└── mapping/                字段路径读取与列表映射
dto/                       网络传输数据，每个顶层类型独立文件
├── SourceItem.kt           归一化后的来源条目
└── HttpPayload.kt          HTTP 响应数据
model/                     配置、规则、结果与错误模型
├── SourceDefinition.kt     来源定义
├── HttpMethod.kt           请求方法
├── HttpRequestSpec.kt      请求配置
├── ItemMapping.kt          条目映射规则
├── JsonFieldEquals.kt      字段匹配条件
├── UrlMapping.kt           链接映射规则
├── NetworkResult.kt        请求结果
├── NetworkError.kt         错误信息
└── ProcessingStage.kt      处理阶段
sources/
└── BuiltInSources.kt       平台请求参数和字段配置
```

`network` 的 DTO 统一放在 `com.aster.core.network.dto`，包括当前的响应数据和统一来源条目，以及后续接口的请求体、响应体 DTO；配置、规则、结果封装和错误信息放在 `com.aster.core.network.model`。每个顶层类型独立成文件。请求执行、提取和映射逻辑放在 `adapters`；`JsonPath` 包含路径解析及读取逻辑，保留在 `adapters.mapping`。

流程为 `来源 ID → SourceDefinition → HTTP 请求 → ContentExtractor → JsonItemMapper → SourceItem`。动态请求直接复用 `NetworkClient.httpClient`，不需要为每个平台创建 Retrofit Service；已有 Service API 仍可独立使用。

### 增加同类来源

请求与字段结构可配置的来源只新增定义，例如一个返回 `data.items` 的 JSON 接口：

```kotlin
import com.aster.core.network.adapters.mapping.JsonPath
import com.aster.core.network.model.HttpRequestSpec
import com.aster.core.network.model.ItemMapping
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.model.UrlMapping
import okhttp3.HttpUrl.Companion.toHttpUrl

val example = SourceDefinition(
    id = "example-news",
    name = "示例新闻",
    request = HttpRequestSpec(
        url = "https://example.com/api/news".toHttpUrl(),
        query = mapOf("page" to "1")
    ),
    mapping = ItemMapping(
        itemsPath = JsonPath("data.items"),
        idPath = JsonPath("id"),
        titlePath = JsonPath("title"),
        url = UrlMapping.Field(JsonPath("url")),
        summaryPath = JsonPath("description")
    )
    // 默认使用 JsonExtractor
)
val adaptersWithExample = AdapterManager(networkClient, BuiltInSources.all + example)
```

- `HttpRequestSpec` 支持 GET、POST、查询参数、每次请求的 headers，以及 POST 的 `JsonElement` 请求体；查询参数自动编码。默认单次正文限制为 2 MiB，按来源用 `maxResponseBytes` 调整。非 2xx 错误正文超过限制时截断保存。
- `JsonExtractor` 接受对象或数组作为 JSON 根节点。内嵌 JSON 使用 `EmbeddedJsonExtractor(startMarker, endMarker)` 按字面标记提取，支持跨行；内容仍须为 JSON，不会执行 JavaScript，也不处理任意 JS 赋值语法。
- `JsonPath` 仅支持字段和非负数组下标，如 `data.cards[0].content`，空字符串表示根节点。列表路径相对文档根节点，条目字段路径相对每条记录；不支持通配符、筛选表达式或含点号的字段名。
- ID、标题和链接为必填项，摘要允许缺失；字段支持字符串或数字，数字 ID 保留原文本精度。链接字段支持相对路径，以重定向后的响应 URL 为基准，仅接受 HTTP/HTTPS。
- `UrlMapping.Template("https://example.com/items/{id}")` 可拼接详情地址，只替换 `{id}`，并将 ID 编码为 URL 组件。
- `requiredRootValues` 检查响应级字段，例如 `ret == 0`；`excludedItems` 按字段值排除条目，例如百度的置顶条目；`allowEmpty` 控制是否允许空结果。内置两份定义要求结果非空。结构变化、必填字段缺失会返回错误，不静默跳过坏数据。
- 来源 ID 必须唯一，重复注册在创建 Manager 时失败；未注册 ID 返回 `SOURCE` 阶段错误，不发出网络请求。

新增一种内容格式时，实现 `ContentExtractor`，将响应转换为 `JsonElement`，并在来源定义中指定该提取器，即可复用后续映射。预期的提取错误应抛出 `AdapterException(ProcessingStage.EXTRACTION, message, cause)`。无需修改 Manager 的平台分支。当前不包含 GraphQL 错误语义处理、HTML DOM 选择器、RSS/Atom、JavaScript 执行或多步登录/签名流程。

内置规则移植自 [NewsNow 的固定版本](https://github.com/newsnext/newsnow/tree/5da836c49c327f2c352216c5cbb4dea1abd9fcd3/server/sources)。MIT 许可保留在 [newsnow-LICENSE.txt](src/main/resources/META-INF/newsnow-LICENSE.txt)，随模块打包。第三方请求参数及页面结构可能变化，届时优先更新来源定义；本模块不建立后端服务。

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
import com.aster.core.network.model.NetworkResult
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

协程取消会传播并取消底层请求；程序错误继续抛出。请求 block 仅放请求及必要的响应转换，业务处理放在结果之后。

`AdapterManager.fetch` 同样返回 `NetworkResult`。HTTP、连接和超时错误沿用上述类型；来源查找、响应大小检查、提取或字段映射失败返回 `NetworkError.Processing(sourceId, stage, message, cause)`，阶段分别为 `SOURCE`、`REQUEST`、`EXTRACTION`、`MAPPING`。JSON 提取失败归为 `EXTRACTION`，以便定位来源规则。

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

模块沿用 OkHttp 默认连接恢复行为，不添加业务层重试。Retrofit Service 可通过注解使用表单及其他 HTTP 方法；来源适配工具目前提供 GET/POST JSON 请求。缓存、鉴权和调度策略由后续具体需求接入。

## 验证

```powershell
.\gradlew.bat :core:network:assembleDebug :core:network:testDebugUnitTest :core:network:lintDebug
```

单元测试通过本地 MockWebServer 验证 GET/POST、JSON、文本及字符集、多 baseUrl、HTTP 错误、超时和取消；适配器测试覆盖两种来源配置、自定义提取器、路径与 URL 映射、异常阶段、响应大小和重定向。

`src/test/resources/adapters` 中的两份响应是人工构造的结构样例，用于验证适配契约；自动化测试不依赖外部平台，也不代表当前线上接口一定可用。
