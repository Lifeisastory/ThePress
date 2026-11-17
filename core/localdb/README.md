# 新闻本地缓存

`NewsDatabase` 使用 Room，数据库文件为 `news.db`，由 Application 在进程内复用。`NewsRepository` 必须注入 `NewsCacheDao`，页面仍通过 `fetch(sourceId)` 加载数据。

- `news_cache`：每个来源最近一次成功请求的时间戳（毫秒）。
- `cached_news_articles`：该来源的完整文章列表，按 `position` 保存原始榜单顺序。
- `NewsCacheDao.read/replace` 使用事务，确保列表和时间戳一致；替换时删除该来源的旧条目，不影响其他来源。空结果也保留时间戳。

缓存有效条件为 `0 <= 当前时间 - fetchedAt < 600000`。命中时只读数据库，不请求网络，也不续期；达到十分钟、首次访问或系统时钟回拨导致时间戳在未来时，重新请求网络。网络成功后整体替换列表并记录成功时间。重启应用后仍可使用未过期缓存。

网络失败不会覆盖缓存或更新有效期，仓库返回原有失败结果；不会用过期数据冒充成功。界面仍可保留此前已展示的内容。同一仓库内同一来源的并发请求串行检查缓存，不同来源可以并行加载。数据库读写异常通过 `onCacheError` 反馈，允许网络结果继续返回；协程取消正常向上传递。

当前 schema 版本为 1，导出的 Room schema 位于 `schemas/`。后续修改表结构时应增加版本并提供迁移。

验证命令：

```powershell
.\gradlew.bat :core:data:testDebugUnitTest :core:localdb:connectedDebugAndroidTest :app:assembleDebug
```

仓库单元测试覆盖缓存命中、十分钟边界、失败重试、来源隔离、空列表、并发、时钟回拨、缓存异常和取消。数据库仪器测试需要设备或模拟器，覆盖列表顺序、事务回滚和重新打开后的持久化。
