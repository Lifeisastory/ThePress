# Feature 模块与导航

当前三个顶层页面为首页（`home`）、收藏（`saved`）、我的（`me`）。收藏和我的仅展示标题占位。

| 位置 | 职责 |
| --- | --- |
| `feature/*/api` | 可序列化的 `NavKey` 和导航标题资源 |
| `feature/*/impl` | 页面、ViewModel、UI 状态、资源、测试和 `EntryProvider` 注册函数 |
| `core/navigation` | 每个 Tab 的返回栈、页面状态保存、ViewModel 生命周期与导航操作 |
| `app/navigation` | 顶层 Tab 的图标与顺序，以及 `NavDisplay` 页面装配 |
| `app/ui/App.kt` | 页面容器、系统边距和悬浮玻璃导航栏 |

首页依赖 `core:data`，通过 `NewsRepository` 读取两个来源。仓库和客户端仍由 Application 复用，首页 ViewModel 在 `HomeRoute` 中取得，并绑定到对应导航条目。切换 Tab 保留首页数据与列表位置；页面重建恢复导航状态，进程重建后重新读取网络数据。

点击其他 Tab 会切换到它自己的返回栈；重选当前 Tab 会返回该 Tab 的根页面。系统返回先弹出当前 Tab 内的页面，再返回此前访问的 Tab；首页根页面交由 Activity 处理返回。

导航参考本地 Now-In-Android 的 `d6abd6d3e562ae0f017888f5d62042316a49a2d6` 版本，并使用同版本的 Navigation 3（1.0.0）和 Lifecycle（2.10.0）。`NavigationState` 与 `Navigator` 保留原版权声明，改动了包名、状态作用域及根页面返回边界；Apache 2.0 许可随 `core:navigation` 打包。

API/实现拆分和状态保存也可参考 [Navigation 3 模块化文档](https://developer.android.com/guide/navigation/navigation-3/modularize)及[状态保存文档](https://developer.android.com/guide/navigation/navigation-3/save-state)。各 feature 的实现不依赖 `app`，也不依赖其他 feature 的实现模块。
