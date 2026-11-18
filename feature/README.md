# Feature 模块与导航

应用包含同级的 `MainScreen`、`SearchScreen` 和 `WebViewScreen` 独立路由页。Main 内包含 `HomePage`、`SavedPage`、`ProfilePage` 三个 Tab 页面，收藏当前仅展示标题占位。独立路由页使用 `XxxScreen` 命名，内部页面使用 `XxxPage`；`ProfilePage` 保留在既有 `feature/me` 模块中，提供外观设置、资讯缓存管理与关于信息。

| 位置 | 职责 |
| --- | --- |
| `feature/*/api` | 对外资源；独立路由功能还提供可序列化的 `NavKey`（当前为 main、search、webview） |
| `feature/*/impl` | Page 或 Screen、ViewModel、UI 状态、资源和测试；独立路由提供 `EntryProvider` 注册函数 |
| `core/navigation` | 应用单一返回栈、路由条目的状态保存与 ViewModel 生命周期、导航操作 |
| `app/navigation` | Main／Search／WebView 的 `NavDisplay` 装配与跨路由跳转 |
| `app/ui/App.kt` | 应用路由宿主入口 |
| `feature/main/api` | MainNavKey，供应用设置起始路由与识别当前路由 |
| `feature/main/impl` | MainScreen、MainTab、各 Page 的状态保存、系统边距、悬浮玻璃导航栏和 mainEntry 注册函数 |
| `feature/me/impl` | ProfilePage、ProfileViewModel，主题与颜色选择、资讯缓存统计与清除、项目地址和致谢 |
| `feature/webview/api`、`feature/webview/impl` | 携带 URL 的 WebViewNavKey、应用内网页、网页历史状态和 webViewEntry 注册函数 |

Main 使用 `rememberSaveable` 保存选中 Tab，用 `SaveableStateHolder` 按 Tab 保存页面滚动位置等 UI 状态。首页 ViewModel 在 `HomePage` 中取得，归属 Main 路由条目的 ViewModelStore，切换 Tab 不会重新创建。搜索拥有独立的 ViewModel，出栈后释放；从搜索返回恢复 Main 原来的 Tab 和页面状态。普通 ViewModel 不负责进程死亡后的数据保存，进程重建后首页仍从仓库缓存恢复数据。

首页和搜索的条目点击通过 `onOpenArticle(url)` 交给应用导航，统一打开 WebViewScreen。顶部悬浮栏使用 AppIconButton 返回／关闭按钮和与首页 Tab 栏同款效果的 AppTitleBar 标题胶囊，展示标题和域名。普通网页加载时，在内容区域中央显示 design_system/component/AppLoadingIndicator 自绘三点动画，完成或失败后隐藏；下拉刷新沿用刷新指示器，不重复显示中央动画。网页滚动到顶部后通过 AppRefreshLayout 下拉刷新，失败时也可点击重试。RefreshWebView 将顶部单指下拉分发给 Compose 嵌套滚动，保留网页其他手势；刷新容器遇到多指操作会取消本次刷新。系统返回与工具栏返回优先回退网页历史，没有历史时退出路由，关闭按钮直接返回原列表。SystemInsets 统一避让状态栏、刘海、底部系统栏和键盘；顶部控件独立占据布局空间，网页视口从工具栏下方开始，不显示 Main 底栏。网页历史通过 WebView.saveState/restoreState 随路由保存，实例在 AndroidView 释放时销毁，并随页面生命周期暂停／恢复。HTTP(S) 链接留在应用内，其他协议不自动拉起外部应用；允许 JavaScript 和 DOM 存储，关闭文件与 content URI 访问，不注入原生 JS 桥，不放行证书错误，也不改变应用的明文流量策略。

WebView 的顶部玻璃控件与刷新指示器使用主题底色作为背景，不实时采样网页。AppRefreshLayout 接收外部 indicatorBackdrop 后跳过内容录制，避免原生 WebView 被重复绘制到离屏图层而闪动；普通列表仍使用原有的内容采样。

AppTheme 通过 LocalAppDarkTheme 提供实际深浅色选择，WebView 使用独立的 Android 主题上下文同步 isLightTheme 和夜间配置。主题切换时重建 WebView 并恢复历史；网站自带的 prefers-color-scheme 样式优先，支持 ALGORITHMIC_DARKENING 的 WebView 在深色模式下允许自动暗化。网站显式禁用暗化或设备 WebView 不支持时保留站点样式，不保证所有网站都变为深色。

ProfilePage 使用带 contentPadding 的 LazyColumn 分组展示设置，复用 Main 的安全区与底栏避让。ProfileViewModel 通过 UserPreferencesRepository 观察和保存主题模式、颜色，MainActivity 观察同一偏好并更新 AppTheme 与系统栏图标。动态取色在 Android 12+ 生效，旧系统退回默认紫色。设置写入失败保留已保存的选项并提示，读取失败自动重试。

资讯缓存的条数与内容大小通过 NewsRepository.observeCacheStats() 实时观察；大小仅统计缓存文本字段的 UTF-8 字节数，不代表整个数据库文件、图片或 WebView 占用。清除操作在页内二次确认，与正在进行的来源刷新互斥，删除快照及其有效期，保留外观和来源排序；清理后首页与搜索通过数据库观察流更新。项目地址和致谢链接通过 Main 的 onOpenArticle 回调进入应用内 WebView。

Tab 切换不修改应用返回栈，也不保留 Tab 访问历史。系统返回先关闭独立路由；在 Main 的收藏或我的页面返回时切回首页，首页根页面交由 Activity 处理退出。主 Tab 通过底部玻璃栏切换，首页的水平滑动只用于切换数据源。

MainScreen 独占玻璃导航栏及其背景采样，计算系统安全区和底栏占用后，通过 `contentPadding` 传给各 Page。HomePage 将头尾留白用于列表内容，列表视口仍延伸到状态栏和底部系统导航栏后方。SearchScreen 自行处理系统栏和键盘安全区，不预留 Main 底栏高度。布局使用 Box 与 SystemInsets，不依赖 Scaffold。

HomeFeed 在 Compose 估算的累计滚动距离超过当前列表视口高度时，通过 `onScrollToTopChanged` 提供平滑回到顶部的动作，否则提供 null。HomePage 只转发当前来源的动作，退出首页时清除；MainScreen 将可见性和点击回调传给 AppNavigationBar。导航栏和右侧按钮由 `NavigationGlassSurface` 统一绘制：同一个动画进度驱动底板宽度、按钮位移和融合细颈，显隐时水滴分离／融合，滑块始终位于按钮上层。裁剪与阴影使用平滑并集轮廓，Android 13+ 通过相同距离场计算折射；旧版本保留轮廓动画并沿用原有模糊／底色降级。组件不依赖列表或业务状态。

首页依赖 `core:data`，通过 `NewsRepository.observeNews()` 持续观察数据库内容，独立调用 `refresh(sourceId)` 更新数据库。刷新结果只报告状态，不携带文章。十分钟内复用已有快照，无缓存或过期时联网并写入数据库，写入失败报告存储错误并保留旧内容；手动刷新同样遵循这一有效期。Application 只持有 `AppDataContainer`，数据库、偏好和客户端由 data 统一管理，app 与 feature 不直接依赖 localdb、localsp 或 network。

点击首页左上角来源图标进入数据源排序。MainScreen 在页面和底栏上方承载同窗口的 `SourceOrderDialog`，通用玻璃外观由 design_system 的 `AppGlassDialog` 提供。弹窗支持长按拖动、邻项让位、边缘自动滚动和无障碍上移／下移；取消不保存，点击完成后写入成功才同步首页顺序。HomeViewModel 通过 data 的 `UserPreferencesRepository` 写入和持续观察顺序，底层 `AppSharedPreferences` 使用进程共享的 Preferences DataStore（沿用 `user_preferences` 文件和 `news_source_order` 键），以 JSON 数组保存有序来源 ID。重复或失效 ID 被忽略，遗漏或新加入的来源按内置默认顺序追加；“全部”固定首位，当前来源与各页滚动位置按稳定 ID 保留。偏好更新不触发新闻刷新。

每个 HomeFeed 用 `core/design_system/component/AppRefreshLayout` 包裹自己的 LazyColumn，父子共用同一个 LazyListState。顶部下拉达到阈值并松手后刷新当前来源，“全部”页刷新全部来源；指示器的避让距离复用列表 contentPadding，列表视口不缩小。横向切换来源时暂停刷新手势，各来源的加载状态独立判断。

刷新指示器由 `component/liquid_glass/AppRefreshIndicator` 绘制，采用胶囊形液态玻璃。AppRefreshLayout 仅记录列表内容供指示器采样，指示器作为兄弟覆盖层绘制，避免重复采样自身。手势状态作为私有实现保留在 AppRefreshLayout.kt 中，行为统一通过 AppRefreshLayoutTest 验证。

AppRefreshLayout 的 isRefreshing、isLoadingMore、hasMore 和请求回调均由调用方提供，内部仅管理手势与指示器。默认阈值为 64dp，刷新与加载互斥；取消、未达阈值、程序滚动和惯性滚动不触发请求。当前首页仅获取整榜快照，没有下一页契约，因此 hasMore 为 false，上拉显示“没有更多了”。后续支持分页的列表可直接接入 onLoadMore，并在成功或失败后复位加载状态。

路由状态保存可参考 [Navigation 3 状态保存文档](https://developer.android.com/guide/navigation/navigation-3/save-state)。各 feature 的实现不依赖 `app`；`feature/main/impl` 作为组合容器依赖 home、saved、me 的页面实现，其他 feature 之间不互相依赖实现模块。Page 通过回调请求上层导航，Main 再将搜索请求交给应用路由宿主处理，不依赖搜索实现。

界面固定文案按所属模块集中维护在 `src/main/res/values/strings.xml`：design_system 保存通用组件文案，core/ui 保存共享资讯 UI 文案，各 feature/impl 保存页面内部文案，feature/api 只公开跨模块使用的功能名称，app 保存应用名称等入口文案。MainTab 引用各 feature/api 的标题资源。Compose 使用 stringResource，非 Compose 场景使用资源 ID 或 getString；动态值使用格式占位符，按语义决定是否共享文案。

新闻标题、摘要和来源名称属于数据，日志与异常中的诊断文字不作为界面文案提取。预览模拟内容可放入 debug 源集；UI 测试从资源读取界面标签，测试模拟数据保留在测试中。网页加载失败与重试文案由 feature/webview/impl 管理。

平台图标只维护在 `core/design_system/src/main/assets/source_icons`，沿用 Android 标准 assets 目录，无需额外的 Gradle 映射。AppIcon 使用 `file:///android_asset/source_icons/<platform-id>.png`，缺省图标为同目录下的 default.png；来源说明和许可文件随图标保留。
