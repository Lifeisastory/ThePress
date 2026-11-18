# 应用数据入口

Application 持有一个 `AppDataContainer`。容器内部创建并复用 `AppDataBase`、`AppSharedPreferences` 和网络客户端，只向外暴露仓库接口。app、feature 不直接调用存储类、DAO 或网络客户端。

- `NewsRepository.observeNews()`：持续观察 Room 中的新闻，包含过期快照，不触发网络。
- `NewsRepository.search()`：持续观察本地搜索结果，不触发网络。
- `NewsRepository.refresh(sourceId)`：按十分钟有效期检查快照，必要时联网并事务替换；仅返回 `NewsRefreshResult`，写入失败报告 `STORAGE`。
- `UserPreferencesRepository.sourceOrder` / `saveSourceOrder()`：观察和保存来源排序，界面通过观察流接收已保存的顺序。

`OfflineFirstNewsRepository` 和 `DefaultUserPreferencesRepository` 是模块内部实现，外部测试可直接实现仓库接口，无需构造存储对象。

两个仓库通过 `DataRequestLogging` 统一记录数据访问：每次请求注明本地数据库、本地偏好或网络来源，输入及响应以 JSON 输出；持续观察按订阅编号记录开始、每次数据响应和结束。详见 [统一日志](../analytics/README.md)。

数据库入口改名后继续使用 `news.db`，偏好仍使用 `user_preferences`，避免更换文件导致已有数据丢失。存储模块保留独立职责，依赖关系为 `app/feature → core:data → core:localdb、core:localsp、core:network`。
