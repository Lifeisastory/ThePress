# 统一日志

`core:analytics` 集中管理应用诊断日志的接口、级别、格式、脱敏和输出。当前输出到 Logcat，不包含远程埋点或上报服务。

## 使用方式

Application 创建并复用 `Analytics.createLogger(this)`，注入 `NewsRepository`。是否启用日志只在此工厂内判断：Debug 使用 `LogcatLogger`，非 Debug 使用 `NoOpLogger`。业务模块依赖 `Logger`，不直接使用 Android `Log`。应用默认不再向 `NetworkClient` 注入日志实例，避免底层 HTTP 调用重复打印；排查多步请求时仍可手动注入，启用原有 HTTP 诊断事件。

```kotlin
logger.log(LogEvent(
    name = "cache_failed",
    category = "cache",
    level = LogLevel.WARN,
    attributes = mapOf("sourceId" to sourceId),
    cause = error
))
```

`LogEvent` 承载事件名称、分类、级别、结构化字段、可选异常和 JSON 类型的 `response`。模块在事件发生处提交日志；`LogFormatter` 统一格式，`LogcatLogger` 是唯一打印出口。测试可以使用 `Logger { events.add(it) }` 收集事件，无需 Android Logcat。

## 已接入事件

| 分类 | 事件 | 内容 |
| --- | --- | --- |
| request | request_started | 请求编号、来源 ID、数据来源 origin、操作 operation |
| request | request_finished | 相同请求编号、耗时、结果及 JSON 响应数据 |
| network | source_failed | 来源 ID、错误类型、HTTP 状态或处理阶段、可选原始异常 |
| cache | cache_failed | 来源 ID、数据库异常 |

Logcat 标签统一为 `ThePress`，例如：

```text
[request] request_started requestId="1" sourceId="baidu" origin="LOCAL_DATABASE" operation="READ"
[request] request_finished requestId="1" sourceId="baidu" origin="LOCAL_DATABASE" operation="READ" durationMs="3" outcome="HIT"
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
            "summary": null
        }
    ],
    "fetchedAt": 1794931200000
}
```

`origin` 为 `NETWORK` 或 `LOCAL_DATABASE`；`operation` 为 `FETCH`、`READ` 或 `WRITE`。数据库读取结果区分 `HIT`、`MISS`、`EXPIRED`，网络和写入成功为 `SUCCESS`，失败为 `FAILED`，取消为 `CANCELED`。请求开始和正常结束使用 DEBUG，失败结束和缓存错误使用 WARN，来源失败诊断使用 ERROR。

每次数据库读取、网络加载和缓存写入都有独立的开始/完成配对。首次加载的顺序为数据库读取未命中 → 网络开始 → 缓存写入开始/完成 → 网络完成；网络耗时包含缓存写入。缓存命中时只有数据库读取。数据库命中/过期响应与网络成功响应都包含统一文章字段，写入成功响应包含已写入列表，空列表输出 `articles: []`。失败响应包含错误原因或异常类型。非 Debug 的空实现不会构造这些响应日志。

## 输出规则

- 输出解析后的结构化文章数据，不提交请求头、请求体或上游原始 HTML/响应正文。
- 格式化层对以 `url` 结尾的字段去除用户信息、查询参数及片段，JSON 响应的嵌套字段也执行此过滤。
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
