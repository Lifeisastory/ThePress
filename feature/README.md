# Feature 模块与导航

应用包含同级的 `MainScreen` 和 `SearchScreen` 独立路由页。Main 内包含 `HomePage`、`SavedPage`、`ProfilePage` 三个 Tab 页面，收藏和我的当前仅展示标题占位。独立路由页使用 `XxxScreen` 命名，内部页面使用 `XxxPage`；`ProfilePage` 暂保留在既有 `feature/me` 模块中。

| 位置 | 职责 |
| --- | --- |
| `feature/*/api` | 对外资源；独立路由功能还提供可序列化的 `NavKey`（当前为 main、search） |
| `feature/*/impl` | Page 或 Screen、ViewModel、UI 状态、资源和测试；独立路由提供 `EntryProvider` 注册函数 |
| `core/navigation` | 应用单一返回栈、路由条目的状态保存与 ViewModel 生命周期、导航操作 |
| `app/navigation` | Main／Search 的 `NavDisplay` 装配与跨路由跳转 |
| `app/ui/App.kt` | 应用路由宿主入口 |
| `feature/main/api` | MainNavKey，供应用设置起始路由与识别当前路由 |
| `feature/main/impl` | MainScreen、MainTab、各 Page 的状态保存、系统边距、悬浮玻璃导航栏和 mainEntry 注册函数 |

Main 使用 `rememberSaveable` 保存选中 Tab，用 `SaveableStateHolder` 按 Tab 保存页面滚动位置等 UI 状态。首页 ViewModel 在 `HomePage` 中取得，归属 Main 路由条目的 ViewModelStore，切换 Tab 不会重新创建。搜索拥有独立的 ViewModel，出栈后释放；从搜索返回恢复 Main 原来的 Tab 和页面状态。普通 ViewModel 不负责进程死亡后的数据保存，进程重建后首页仍从仓库缓存恢复数据。

Tab 切换不修改应用返回栈，也不保留 Tab 访问历史。系统返回先关闭独立路由；在 Main 的收藏或我的页面返回时切回首页，首页根页面交由 Activity 处理退出。主 Tab 通过底部玻璃栏切换，首页的水平滑动只用于切换数据源。

MainScreen 独占玻璃导航栏及其背景采样，计算系统安全区和底栏占用后，通过 `contentPadding` 传给各 Page。HomePage 将头尾留白用于列表内容，列表视口仍延伸到状态栏和底部系统导航栏后方。SearchScreen 自行处理系统栏和键盘安全区，不预留 Main 底栏高度。布局使用 Box 与 SystemInsets，不依赖 Scaffold。

HomeFeed 在 Compose 估算的累计滚动距离超过当前列表视口高度时，通过 `onScrollToTopChanged` 提供平滑回到顶部的动作，否则提供 null。HomePage 只转发当前来源的动作，退出首页时清除；MainScreen 将可见性和点击回调传给 AppNavigationBar。导航栏和右侧按钮由 `NavigationGlassSurface` 统一绘制：同一个动画进度驱动底板宽度、按钮位移和融合细颈，显隐时水滴分离／融合，滑块始终位于按钮上层。裁剪与阴影使用平滑并集轮廓，Android 13+ 通过相同距离场计算折射；旧版本保留轮廓动画并沿用原有模糊／底色降级。组件不依赖列表或业务状态。

首页依赖 `core:data`，通过 `NewsRepository` 读取来源。仓库、客户端和 `core:localdb` 数据库由 Application 复用；十分钟内的数据库缓存直接复用，无缓存或缓存过期时请求网络，成功后更新缓存。手动刷新同样遵循这一有效期。

点击首页左上角来源图标进入数据源排序。MainScreen 在页面和底栏上方承载同窗口的 `SourceOrderDialog`，通用玻璃外观由 design_system 的 `AppGlassDialog` 提供。弹窗支持长按拖动、邻项让位、边缘自动滚动和无障碍上移／下移；取消不保存，点击完成后写入成功才同步首页顺序。`core/localsp/SourceOrderPreferences` 使用进程共享的 Preferences DataStore（`user_preferences` 文件、`news_source_order` 键），以 JSON 数组保存有序来源 ID。HomeViewModel 启动时恢复顺序，忽略重复或失效 ID，新来源追加到末尾；“全部”固定首位，当前来源与各页滚动位置按稳定 ID 保留。

每个 HomeFeed 用 `core/design_system/component/AppRefreshLayout` 包裹自己的 LazyColumn，父子共用同一个 LazyListState。顶部下拉达到阈值并松手后刷新当前来源，“全部”页刷新全部来源；指示器的避让距离复用列表 contentPadding，列表视口不缩小。横向切换来源时暂停刷新手势，各来源的加载状态独立判断。

刷新指示器由 `component/liquid_glass/AppRefreshIndicator` 绘制，采用胶囊形液态玻璃。AppRefreshLayout 仅记录列表内容供指示器采样，指示器作为兄弟覆盖层绘制，避免重复采样自身。手势状态作为私有实现保留在 AppRefreshLayout.kt 中，行为统一通过 AppRefreshLayoutTest 验证。

AppRefreshLayout 的 isRefreshing、isLoadingMore、hasMore 和请求回调均由调用方提供，内部仅管理手势与指示器。默认阈值为 64dp，刷新与加载互斥；取消、未达阈值、程序滚动和惯性滚动不触发请求。当前首页仅获取整榜快照，没有下一页契约，因此 hasMore 为 false，上拉显示“没有更多了”。后续支持分页的列表可直接接入 onLoadMore，并在成功或失败后复位加载状态。

路由状态保存可参考 [Navigation 3 状态保存文档](https://developer.android.com/guide/navigation/navigation-3/save-state)。各 feature 的实现不依赖 `app`；`feature/main/impl` 作为组合容器依赖 home、saved、me 的页面实现，其他 feature 之间不互相依赖实现模块。Page 通过回调请求上层导航，Main 再将搜索请求交给应用路由宿主处理，不依赖搜索实现。

界面固定文案按所属模块集中维护在 `src/main/res/values/strings.xml`：design_system 保存通用组件文案，core/ui 保存共享资讯 UI 文案，各 feature/impl 保存页面内部文案，feature/api 只公开跨模块使用的功能名称，app 保存应用名称等入口文案。MainTab 引用各 feature/api 的标题资源。Compose 使用 stringResource，非 Compose 场景使用资源 ID 或 getString；动态值使用格式占位符，按语义决定是否共享文案。

新闻标题、摘要和来源名称属于数据，日志与异常中的诊断文字不作为界面文案提取。预览模拟内容可放入 debug 源集；UI 测试从资源读取界面标签，测试模拟数据保留在测试中。新闻原文打开失败的提示由 core/ui 的 ui_article_open_failed 统一提供。

平台图标只维护在 `core/design_system/src/main/assets/source_icons`，沿用 Android 标准 assets 目录，无需额外的 Gradle 映射。AppIcon 使用 `file:///android_asset/source_icons/<platform-id>.png`，缺省图标为同目录下的 default.png；来源说明和许可文件随图标保留。
