# 项目共享数据模型

`core:model` 存放项目中供多个模块共享的数据模型，统一包名为 `com.aster.core.model`。

源码目录：`src/main/kotlin/com/aster/core/model/`。

| 模型 | 职责 |
| --- | --- |
| `AgentMessage` / `AgentStep` | 可持久化的助手消息、工具与技能步骤、引用和结束状态 |
| `AgentEvent` | 文本增量、步骤开始/结束、来源更新和运行结束事件，不携带模型凭据 |
| `NewsArticle` | 资讯条目，包含来源 ID、条目 ID、标题、原文链接、可选摘要与配图链接。 |
| `NewsSource` | 数据来源的 ID 与展示名称。 |
| `NetworkResult<T>` | 请求或来源处理的成功数据与失败详情；成功结果携带 `T`。 |
| `NetworkError` | HTTP、连接、超时、反序列化和来源处理失败的详细信息。 |
| `ProcessingStage` | 来源查找、请求、提取和映射的错误定位阶段。 |
| `HttpHeaders` | 不依赖 HTTP 客户端的响应头，保留顺序与重复值，支持大小写不敏感查询。 |
| `NewsRefreshResult` | 资讯刷新的成功或失败状态，不携带文章；文章通过数据库观察流提供。 |
| `NewsFailureReason` | 连接失败、超时、HTTP 错误、数据异常和本地存储失败等原因。 |
| `AppearanceSettings`、`ThemeMode`、`ThemeColor` | 持久化的显示模式和配色选项，默认跟随系统并使用动态取色。 |
| `NewsCacheStats` | 资讯缓存条目数与文本内容字节数。 |

数据层、功能模块和共享 UI 均可使用这些模型。模块只定义共享数据结构，不包含网络请求、数据库操作、仓库实现或 UI 逻辑，也不反向依赖这些业务模块。

网络结果和刷新结果共同存放在此模块，但保留不同语义：`NetworkResult.Success<T>` 表示取得数据，`NewsRefreshResult.Success` 表示刷新流程完成（包括缓存命中或成功落库）。底层网络错误携带诊断详情，`NewsFailureReason` 是界面使用的失败分类，并额外包含本地存储失败。二者的转换仍由 `core:data` 的仓库负责。

`NetworkError.Http.headers` 使用 `HttpHeaders`，由网络层将 OkHttp 响应头转换后传入；`headers[name]` 不区分大小写并返回最后一个值，`headers.values(name)` 按原顺序返回全部匹配值。反序列化错误以 `Throwable` 保留原始异常，模型不暴露具体序列化实现的异常类型，也不依赖 OkHttp 或 Retrofit。

网络 DTO 保留在 `core:network`，数据库实体保留在 `core:localdb`；`core:data` 负责将它们转换为共享模型。页面专用的 UI 状态仍放在对应功能模块。

直接使用共享模型的模块可声明：

```kotlin
implementation(project(":core:model"))
```

如果模型出现在库模块的公开 API 中，应使用 `api(project(":core:model"))` 暴露该依赖；当前 `core:network` 的请求结果、`core:data` 的仓库和 `core:ui` 的卡片均属于这种情况。
