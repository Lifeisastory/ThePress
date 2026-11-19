# 应用数据入口

Application 持有一个 `AppData`。容器内部创建并复用 `AppDataBase`、`AppSharedPreferences` 和网络客户端，只向外暴露仓库接口。app、feature 不直接调用存储类、DAO 或网络客户端。

模块内按职责划分包：

- `repository`：仓库接口与实现，协调数据读取、写入和刷新。
- `mapper`：网络 DTO、数据库实体与共享业务模型之间的转换。

`NewsLogResponse.kt` 集中处理新闻、缓存、刷新结果及来源加载失败的业务日志转换，由仓库调用；转换函数为 `internal`，事件名称、级别和字段由数据层决定。通用请求日志由 `core:analytics` 的 `RequestLogging` 提供；新闻摘要生成、响应校验及相关类型归属 `core:agent`，共享业务模型放在 `core:model`。

数据库实体和 DAO 分别位于 `core:localdb` 的 `entity`、`dao` 包，网络 DTO 位于 `core:network` 的 `dto` 包。

网络结果 `NetworkResult`、错误详情 `NetworkError` 与业务结果 `NewsRefreshResult`、失败分类 `NewsFailureReason` 都位于 `core:model`。仓库仍负责将底层错误映射为业务失败；网络请求成功但落库失败时返回 `STORAGE`，缓存命中也可直接返回刷新成功。

- `NewsRepository.observeNews()`：持续观察 Room 中的新闻，包含过期快照，不触发网络。
- `NewsRepository.search()`：持续观察本地搜索结果，不触发网络。
- `NewsRepository.refresh(sourceId)`：按十分钟有效期检查快照，必要时联网并事务替换；仅返回 `NewsRefreshResult`，写入失败报告 `STORAGE`。
- `UserPreferencesRepository.sourceOrder` / `saveSourceOrder()`：观察和保存来源排序，界面通过观察流接收已保存的顺序。
- `UserPreferencesRepository.appearance` / `saveThemeMode()` / `saveThemeColor()`：观察和分别保存显示模式、主题色；沿用同一个 DataStore，未知值退回跟随系统和动态取色。
- `NewsRepository.observeCacheStats()`：观察缓存条数与文本字段字节数，不扫描或估算整个应用目录。
- `NewsRepository.clearCache()`：与来源刷新互斥，删除所有资讯快照；外键级联删除文章，并移除刷新有效期。设置不受影响，删除失败向调用方报告。

`OfflineFirstNewsRepository` 和 `DefaultUserPreferencesRepository` 是模块内部实现，外部测试可直接实现仓库接口，无需构造存储对象。

仓库通过 `RequestLogging` 统一记录数据访问：每次请求注明本地数据库、本地偏好或网络来源，输入及响应以 JSON 输出；持续观察按订阅编号记录开始、每次数据响应和结束。业务转换返回带有显式状态的 `LogResponse`，较长的响应构造放在本模块的转换函数中。日志构造及输出异常不会将成功写入误判为存储失败；来源失败和缓存错误的独立事件使用 `log`。详见 [统一日志](../analytics/README.md)。

`DefaultNewsDigestRepository` 读取新闻、配置和密钥，调用 `core:agent` 的 `DigestGenerator`，再保存生成结果。生成器只接收输入并返回摘要条目，生成异常由仓库映射为 `DigestGenerationResult`；数据库和偏好访问仍由仓库协调。详见 [摘要生成能力](../agent/README.md)。

数据库入口改名后继续使用 `news.db`，偏好仍使用 `user_preferences`，避免更换文件导致已有数据丢失。依赖方向为 `app/feature → core:data → core:agent、core:localdb、core:localsp、core:network`；`data` 和 `agent` 共同依赖 `core:model`、`core:analytics`，`agent` 不依赖 `data`。
