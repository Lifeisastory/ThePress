# 统一日志

`core:analytics` 集中管理应用诊断日志的接口、级别、格式、脱敏和输出。当前输出到 Logcat，不包含远程埋点或上报服务。

## 使用方式

Application 创建并复用 `Analytics.createLogger(this)`，交给 `AppData` 注入内部的 `OfflineFirstNewsRepository` 和 `DefaultUserPreferencesRepository`。是否启用日志只在此工厂内判断：Debug 使用 `LogcatLogger`，非 Debug 使用 `NoOpLogger`。业务模块依赖 `Logger`，不直接使用 Android `Log`。

```kotlin
logger.log {
    LogEvent(
        name = "cache_failed",
        category = "cache",
        level = LogLevel.WARN,
        attributes = mapOf("sourceId" to sourceId),
        cause = error
    )
}
```

`LogEvent` 承载事件名称、分类、级别、结构化字段、可选异常和 JSON 类型的 `request` / `response`。数据仓库和摘要生成器使用本模块的 `RequestLogging` 记录单次访问或 Flow 订阅；`LogFormatter` 统一格式，`LogcatLogger` 是唯一打印出口。测试可以使用 `Logger { events.add(it) }` 收集事件，无需 Android Logcat。

`RequestLogging.trace()`、`observe()` 统一管理请求编号、耗时、成功、失败与取消。调用方显式传入 `resource`，`request` 返回 JSON，`describe` 返回 `LogResponse(outcome, payload)`；`outcome` 使用 `LogOutcome` 枚举，`payload` 只承载业务字段。框架根据显式状态生成日志属性和响应 JSON 中的 `outcome`，忽略 payload 中同名字段，不要求调用方手动填写 JSON 状态。新闻、缓存等业务转换保留在 `core:data`，摘要转换保留在 `core:agent`，本模块不依赖这些业务模块。公开 API 使用 `Flow` 和 JSON 类型，因此通过 `api` 暴露协程及序列化 JSON 依赖。

日志构造和输出与业务异常处理分开：普通日志异常不会阻止或重试业务操作，不改变返回值、Flow 发射顺序或订阅次数，也不会替换业务异常或协程取消。请求描述失败时保留 `loggingErrorType`；响应描述失败时记为 `UNKNOWN`，而非业务失败。诊断只记录日志异常的类型，不记录异常消息。输出失败时丢弃该条日志，不通过同一个出口递归报错。

单条事件使用 `Logger.log { ... }`，同样隔离同步日志构造及输出的 `Exception`；回调内只放日志代码。日志回调自行抛出的 `CancellationException` 也按日志故障隔离，业务 block、上游 Flow 和下游收集者的取消仍原样传播。这些保护不捕获 JVM 致命错误，日志回调仍同步执行，应保持轻量。`NoOpLogger` 不执行请求、响应或事件构造回调。

## 已接入事件

| 分类 | 事件 | 内容 |
| --- | --- | --- |
| request | request_started | 请求编号、可选来源 ID、数据来源 origin、操作 operation、资源 resource 和 JSON 请求参数 |
| request | request_response | 观察流每次发出的 JSON 数据，共享订阅编号，包含 sequence 和 elapsedMs |
| request | request_finished | 相同请求编号、耗时、结果及 JSON 响应；观察结束时包含 responseCount |
| network | source_failed | 来源 ID、错误类型、HTTP 状态或处理阶段、可选原始异常 |
| cache | cache_failed | 来源 ID、数据库异常 |

Logcat 标签统一为 `ThePress`，例如：

```text
[request] request_started requestId="1" sourceId="baidu" origin="LOCAL_DATABASE" operation="READ" resource="news"
request=
{
    "sourceId": "baidu"
}
[request] request_finished requestId="1" sourceId="baidu" origin="LOCAL_DATABASE" operation="READ" resource="news" durationMs="3" outcome="HIT"
response=
{
    "outcome": "HIT",
    "count": 1,
    "articles": [
        {
            "sourceId": "baidu",
            "id": "news-1",
            "title": "示例新闻",
            "url": "https://example.com/news/1",
            "summary": null,
            "imageUrl": null
        }
    ],
    "fetchedAt": 1794931200000
}
```

`origin` 标记 `NETWORK`、`LOCAL_DATABASE`、`LOCAL_PREFERENCES` 或 `LOCAL_PROCESSING`；`operation`、`resource` 由调用方描述操作和资源。数据库有效期检查区分 `HIT`、`MISS`、`EXPIRED`，网络、写入和流数据响应为 `SUCCESS`，失败为 `FAILED`，取消为 `CANCELED`，流自然结束为 `COMPLETED`，摘要遗漏补全为 `PARTIAL`，日志无法描述结果为 `UNKNOWN`。请求开始及正常响应使用 DEBUG，`FAILED`、`UNKNOWN` 和缓存错误使用 WARN，来源失败诊断使用 ERROR。

每次有效期检查、网络加载和数据库写入都有独立的开始/完成配对。一次刷新未命中时的顺序为数据库读取未命中 → 网络开始/完成 → 数据库写入开始/完成；网络耗时包含适配器抓取与解析，不再包含数据库写入。命中时该次刷新只有数据库读取。数据库命中/过期响应与网络成功响应都包含统一文章字段，写入请求及成功响应包含文章列表，空列表输出 `articles: []`。网络成功与写入失败分别记录，不将网络成功误认为持久化成功。

首页观察、本地搜索和偏好观察均在实际收集 Flow 时记录 `request_started`，每个收集者拥有独立 `requestId`。每次向调用方发出数据，打印同编号的 `request_response`，`sequence` 从 1 递增；订阅终止时打印 `request_finished`。`first()`、`take()` 或页面销毁造成的取消正常记录为 `CANCELED`，原始异常与取消信号继续传播。日志不增加额外订阅或数据库查询。`elapsedMs` 和结束的 `durationMs` 表示订阅存续时间，不代表单次 SQL 耗时。

搜索请求包含关键词及可选来源 ID，响应包含命中文章；偏好请求包含键名，写入还包含去重、过滤空值后的来源 ID，响应包含有序 `sourceIds` 和 `count`。观察流与刷新日志可能交错，通过 `requestId`、`origin`、`operation` 区分。非 Debug 的空实现不构造请求和响应日志。

## 输出规则

- 输出业务请求参数及解析后的结构化文章、偏好数据，不提交 HTTP 请求头、原始 HTTP 请求体或上游原始 HTML/响应正文。
- 格式化层对以 `url` 结尾的字段去除用户信息、查询参数及片段，JSON 请求和响应的嵌套字段也执行此过滤。
- 包含 authorization、cookie、token、password、secret、apikey 的字段名会被遮盖，匹配时忽略大小写、下划线和连字符。
- 字段中的换行和控制分隔符转义输出。
- 异常保留类型、调用栈和 cause 链，不直接打印可能携带完整 URL、凭据或正文的异常消息。
- 字段过滤不能识别任意文本中的所有秘密，调用方仍应只提交必要的诊断字段。
- `LogcatLogger` 支持最低级别过滤；输出异常不会中断网络或仓库流程。
- 长日志按最多 3000 UTF-8 字节分段，每段携带相同 `event` 编号和 `part=当前段/总段数`，不拆开 Unicode 代理对。按段号拼接正文可还原完整日志。

验证命令：

```powershell
.\gradlew.bat :core:analytics:testDebugUnitTest :core:network:testDebugUnitTest :core:data:testDebugUnitTest :core:analytics:lintDebug :app:assembleDebug
```
