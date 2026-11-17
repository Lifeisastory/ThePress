# network

网络请求与来源适配模块，提供共享 OkHttp 客户端、Retrofit Service、协程结果封装，以及按来源配置选择的请求、提取和映射工具。

应用默认在 `NewsRepository` 层通过统一 `Logger` 记录数据请求的开始/完成，明确标记 `NETWORK` 或 `LOCAL_DATABASE`，完成时输出解析后的文章 JSON。命中缓存时仍有数据库读取日志；来源加载失败继续提交 `source_failed`。如需单独排查 HTTP 子请求，可向 `NetworkClient` 传入 `logger`，启用 `RequestLoggingListener` 的底层生命周期日志；应用默认关闭这层额外输出。日志开关、级别、格式、脱敏和长响应分段由 `core:analytics` 管理，统一标签为 `ThePress`。详见 [统一日志](../analytics/README.md)。

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

内置配置覆盖上游 `server/sources` 的 46 个平台（44 个文件、2 个子目录），共 57 个独立来源；上游指向同一榜单的别名不重复注册。完整注册表见 [BuiltInSources.kt](src/main/kotlin/com/aster/core/network/sources/BuiltInSources.kt)。原有 `tencentVideo`、`baidu` 属性和 ID 保持不变。

| 平台 | 新增来源 ID | 处理方式 |
| --- | --- | --- |
| AI 热点 | `aihot` | RSS |
| 哔哩哔哩 | `bilibili-hot-search`、`bilibili-hot-video`、`bilibili-ranking` | JSON |
| 参考消息 | `cankaoxiaoxi` | 三频道合并、按时间排序；使用 HTTPS |
| 虫部落 | `chongbuluo-hot`、`chongbuluo-latest` | HTML / RSS |
| 财联社 | `cls-telegraph`、`cls-depth`、`cls-hot` | 动态查询签名、JSON |
| 酷安 | `coolapk` | 动态请求 Token、JSON |
| 懂球帝 | `dongqiudi` | JSON、链接回退 |
| 豆瓣 | `douban` | JSON、Referer |
| 抖音 | `douyin` | 匿名 Cookie 初始化、JSON |
| FastBull | `fastbull-express`、`fastbull-news` | HTML |
| FreeBuf | `freebuf` | RSS |
| 格隆汇 | `gelonghui` | HTML |
| 果核剥壳 | `ghxi` | HTML，兼容当前及旧版列表 |
| GitHub | `github-trending-today` | HTML |
| Hacker News | `hackernews` | HTML，失败回退 RSS |
| 虎扑 | `hupu` | HTML |
| 凤凰网 | `ifeng` | 脚本变量中的 JSON |
| 爱奇艺 | `iqiyi-hot-ranklist` | JSON |
| IT之家 | `ithome` | HTML、过滤推广 |
| 金十数据 | `jin10` | 脚本变量中的 JSON、过滤与文本清理 |
| 掘金 | `juejin` | JSON |
| 靠谱新闻 | `kaopu` | RSS |
| 36氪 | `36kr-quick`、`36kr-renqi` | HTML、动态日期、人气榜回退快讯 |
| 快手 | `kuaishou` | 内嵌 Apollo 缓存引用展开 |
| LINUX DO | `linuxdo-latest`、`linuxdo-hot` | JSON、过滤置顶和归档 |
| MKTNews | `mktnews-flash` | JSON、标题回退 |
| 牛客 | `nowcoder` | JSON、按内容类型构造链接 |
| 远景论坛 | `pcbeta-windows11`、`pcbeta-windows` | Cookie 会话、有限次表单确认、RSS |
| Product Hunt | `producthunt` | 默认 Feed；传入 Token 后 GraphQL，失败回退 Feed |
| 什么值得买 | `smzdm` | HTML，忽略标题前的图片标记链接 |
| Solidot | `solidot` | RSS |
| 卫星通讯社 | `sputniknewscn` | HTML |
| 少数派 | `sspai` | JSON、动态时间戳 |
| Steam | `steam` | HTML |
| 腾讯新闻 | `tencent-hot` | JSON |
| 澎湃新闻 | `thepaper` | JSON |
| 百度贴吧 | `tieba` | JSON |
| 今日头条 | `toutiao` | JSON |
| V2EX | `v2ex-share` | 四频道合并、排序、去重 |
| 华尔街见闻 | `wallstreetcn-quick`、`wallstreetcn-news`、`wallstreetcn-hot` | JSON、字段回退与过滤 |
| 微博 | `weibo` | HTML、可注入 Cookie |
| 雪球 | `xueqiu-hotstock` | 匿名 Cookie 初始化、JSON |
| 早晨报 | `zaobao` | HTML、GB18030 解码，对应上游 `zaobao.ts` 的实际站点 |
| 知乎 | `zhihu` | JSON |

`SourceItem` 包含 `sourceId`、`id`、`title`、`url` 和可空的 `summary`。结果保留来源顺序，同一来源按 ID 去重；不生成来源没有提供的发布时间。

调用方不必传入“HTTP 接口”“内嵌 JSON”等文字：这些选择已经写入 `SourceDefinition`。显示名称与稳定 ID 分离，同一平台有多个榜单时可以注册多个 ID。通用请求、提取和映射放在 `adapters`；平台的端点、字段、签名组合与过滤规则放在独立的 `sources/*Sources.kt` 中，Manager 没有平台分支。

### 需要凭据的来源

```kotlin
import com.aster.core.network.model.SourceCredentials

val definitions = BuiltInSources.create(
    SourceCredentials(productHuntToken = tokenFromSettings, weiboCookie = cookieFromSettings)
)
val adapters = AdapterManager(networkClient, definitions)
```

两个参数都可空；`BuiltInSources.all` 等价于不传凭据的 `create()`。凭据由宿主获取、保存和注入，不硬编码上游示例 Cookie。未传微博 Cookie 时尝试公开页面，返回访客验证页会明确失败。此模块不实现账号登录界面。

### 工具结构

```text
adapters/
├── AdapterManager.kt       来源查找与调用流程
├── AdapterException.kt     工具处理异常
├── request/                HTTP、批量请求、独立 Cookie 会话、表单确认、签名工具
├── extract/                JSON、内嵌/赋值 JSON、HTML DOM、RSS/Atom 提取
└── mapping/                字段路径、文档读取/合并、文本清理与列表映射
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
├── BuiltInSources.kt       唯一公共注册出口和凭据注入
├── SourceDefaults.kt       定义构造的公共默认值
└── *Sources.kt             每个平台的独立规则
```

`network` 的 DTO 统一放在 `com.aster.core.network.dto`，包括当前的响应数据和统一来源条目，以及后续接口的请求体、响应体 DTO；配置、规则、结果封装和错误信息放在 `com.aster.core.network.model`。每个顶层类型独立成文件。请求执行、提取和映射逻辑放在 `adapters`；`JsonPath` 包含路径解析及读取逻辑，保留在 `adapters.mapping`。

流程为 `来源 ID → SourceDefinition → prepareRequest → SourceRequestStrategy → ContentExtractor → transform → JsonItemMapper → SourceItem`。动态请求复用 `NetworkClient.httpClient`，不需要为每个平台创建 Retrofit Service；已有 Service API 仍可独立使用。

- `prepareRequest` 在每次抓取时计算时间戳、日期、签名和请求 Token，避免注册时计算后过期。
- `SourceRequestStrategy.Direct` 为默认单请求；`BatchRequests` 并发抓取并按配置顺序返回，任一请求失败则整体失败，不把缺失频道当作成功。
- `CookieBootstrapRequest`、`FormChallengeRequest` 每次抓取创建独立 CookieJar，遵守域、路径、Secure 和过期规则，复用连接池但不串用来源会话；表单确认默认最多两轮。
- `transform` 接收所有提取结果，负责来源需要的合并、排序、筛选和字段整理，默认要求单个文档。共享 JSON 读取工具在结构不匹配时抛出 `MAPPING` 错误；最终映射保留统一 DTO 契约。
- `fallback` 只在预期请求/处理失败时执行，取消不触发回退。回退结果仍使用调用方请求的 `sourceId`；多个来源相同 ID 的条目不会互相去重。

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

- `HttpRequestSpec` 支持 GET、POST、查询参数、每次请求的 headers，以及互斥的 JSON / 表单请求体；参数自动编码。默认单次正文限制为 2 MiB，按来源用 `maxResponseBytes` 调整。非 2xx 错误正文超过限制时截断保存。`responseCharset` 可覆盖缺失或错误的响应字符集；`acceptedStatusCodes` 仅用于需要读取特定状态验证页的流程，默认仍拒绝所有非 2xx。
- `JsonExtractor` 接受对象或数组作为 JSON 根节点。内嵌 JSON 使用 `EmbeddedJsonExtractor(startMarker, endMarker)` 按字面标记提取，支持跨行；内容仍须为 JSON，不会执行 JavaScript，也不处理任意 JS 赋值语法。
- `AssignedJsonExtractor(variable)` 支持变量赋值中的 JSON 对象/数组，扫描字符串转义与括号边界，不执行表达式。`HtmlListExtractor` 使用 Jsoup CSS 选择器提取文本/属性，空列表选择器结果是提取错误。`FeedExtractor` 支持 RSS 2.0 / Atom，优先 GUID/ID，缺失时用链接，摘要转纯文本；拒绝 DTD。统一映射依旧只接受 HTTP/HTTPS 链接。
- `JsonPath` 仅支持字段和非负数组下标，如 `data.cards[0].content`，空字符串表示根节点。列表路径相对文档根节点，条目字段路径相对每条记录；不支持通配符、筛选表达式或含点号的字段名。
- ID、标题和链接为必填项，摘要允许缺失；字段支持字符串或数字，数字 ID 保留原文本精度。链接字段支持相对路径，以重定向后的响应 URL 为基准，仅接受 HTTP/HTTPS。
- `UrlMapping.Template("https://example.com/items/{id}")` 可拼接详情地址，只替换 `{id}`，并将 ID 编码为 URL 组件。
- `requiredRootValues` 检查响应级字段，例如 `ret == 0`；`excludedItems` 按字段值排除条目，例如百度的置顶条目；`allowEmpty` 控制是否允许空结果。所有内置定义要求结果非空。结构变化、必填字段缺失会返回错误；只跳过来源规则明确排除的广告、置顶或不支持的内容类型。
- 来源 ID 必须唯一，重复注册在创建 Manager 时失败；未注册 ID 返回 `SOURCE` 阶段错误，不发出网络请求。

新增一种内容格式时，实现 `ContentExtractor`，将响应转换为 `JsonElement`，并在来源定义中指定该提取器，即可复用后续映射。预期的提取错误应抛出 `AdapterException(ProcessingStage.EXTRACTION, message, cause)`。新增平台时添加独立配置文件、在 `BuiltInSources.create()` 注册，并补充对应固定响应契约。

移植保持统一条目模型，不包含上游的图标、热度或展示标签。HTML 列表默认保持页面顺序，JSON 合并类来源按上游时间字段排序；不生成抓取时间作为发布时间。FreeBuf 不移植 Node.js 的外部 `curl` 回退，所有请求统一走 OkHttp。远景论坛只识别上游已有的两种表单，不运行任意验证脚本。来源可用性受网络、接口变更和访客验证影响，注册并不保证每次线上请求成功。

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

模块沿用 OkHttp 默认连接恢复行为，不添加全局业务重试；只有配置了 `fallback` 或表单确认策略的来源执行相应的后续请求。缓存和调度仍由调用方负责。

## 验证

```powershell
.\gradlew.bat :core:network:assembleDebug :core:network:testDebugUnitTest :core:data:testDebugUnitTest :core:network:lintDebug
```

单元测试通过本地 MockWebServer 验证 GET/POST、JSON、表单、字符集、批量请求、会话隔离、回退、HTTP 错误、超时和取消。57 个来源都有固定响应契约，另外覆盖 HTML、RSS/Atom、脚本 JSON、路径和 URL 映射、异常阶段、响应大小与重定向。本地 JVM 测试使用 OkHttp JVM 变体，以读取公共后缀资源；Android 应用依然使用 Android 变体及其 Startup 初始化器。

`src/test/resources/adapters` 中的响应是人工构造的结构样例，用于验证适配契约；默认测试不访问外网，也不代表当前线上接口一定可用。

显式执行真实请求探测（也可以将 `all` 换成逗号分隔的来源 ID）：

```powershell
$env:THEPRESS_LIVE_SOURCES = 'all'
# 可选，使用本机已有代理：
# $env:THEPRESS_LIVE_PROXY = 'http://127.0.0.1:7890'
.\gradlew.bat :core:network:testDebugUnitTest --tests '*SourceLiveSmokeTest' --rerun-tasks
Remove-Item Env:THEPRESS_LIVE_SOURCES
```

报告在 `core/network/build/reports/sources/live.json`；有来源失败时探测测试会失败，详情按来源记录，不将失败掩盖成空列表。探测默认不使用账号凭据，每次最多四个来源并发。

2026-11-17 本机代理环境的最后一轮全量探测：52/57 成功。`fastbull-express`、`fastbull-news` 为 TLS 握手失败；`linuxdo-hot` 返回 HTTP 403（此前一轮成功）；`pcbeta-windows` 返回非 Feed 页面；`weibo` 返回访客验证页。以上保留配置并返回明确错误，不据此关闭平台。Product Hunt 默认 Feed 已验证，真实 Token 路径仅做本地契约/回退测试；实际账号凭据未使用。
