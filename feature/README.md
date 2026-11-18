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

首页依赖 `core:data`，通过 `NewsRepository` 读取来源。仓库、客户端和 `core:localdb` 数据库由 Application 复用；十分钟内的数据库缓存直接复用，无缓存或缓存过期时请求网络，成功后更新缓存。手动刷新同样遵循这一有效期。

路由状态保存可参考 [Navigation 3 状态保存文档](https://developer.android.com/guide/navigation/navigation-3/save-state)。各 feature 的实现不依赖 `app`；`feature/main/impl` 作为组合容器依赖 home、saved、me 的页面实现，其他 feature 之间不互相依赖实现模块。Page 通过回调请求上层导航，Main 再将搜索请求交给应用路由宿主处理，不依赖搜索实现。
