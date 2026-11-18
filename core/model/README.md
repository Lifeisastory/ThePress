# 项目共享数据模型

`core:model` 存放项目中供多个模块共享的数据模型，统一包名为 `com.aster.core.model`。

源码目录：`src/main/kotlin/com/aster/core/model/`。

| 模型 | 职责 |
| --- | --- |
| `NewsArticle` | 资讯条目，包含来源 ID、条目 ID、标题、原文链接、可选摘要与配图链接。 |
| `NewsSource` | 数据来源的 ID 与展示名称。 |
| `NewsRefreshResult` | 资讯刷新的成功或失败状态，不携带文章；文章通过数据库观察流提供。 |
| `NewsFailureReason` | 连接失败、超时、HTTP 错误、数据异常和本地存储失败等原因。 |

数据层、功能模块和共享 UI 均可使用这些模型。模块只定义共享数据结构，不包含网络请求、数据库操作、仓库实现或 UI 逻辑，也不反向依赖这些业务模块。

网络 DTO 保留在 `core:network`，数据库实体保留在 `core:localdb`；`core:data` 负责将它们转换为共享模型。页面专用的 UI 状态仍放在对应功能模块。

直接使用共享模型的模块可声明：

```kotlin
implementation(project(":core:model"))
```

如果模型出现在库模块的公开 API 中，应使用 `api(project(":core:model"))` 暴露该依赖；当前 `core:data` 的仓库和 `core:ui` 的卡片均属于这种情况。
