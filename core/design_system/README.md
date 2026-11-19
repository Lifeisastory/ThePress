[](https://)

`AppInputBar` 是通用液态玻璃输入容器，支持多行、IME 操作与图标插槽；`AppSearchBar` 复用该容器保留单行搜索行为，`AppIconButton` 支持禁用态，玻璃组件的 backdrop 只采样兄弟内容节点

`AppExpandingInputBar` 复用 `AppNavigationSurface` 的融合轮廓和材质，以单行输入保持与底部导航相同的高度，展开状态由调用方控制，提交后的回收动画不重绘两层玻璃

`AppSideBySidePane` 将左侧半屏和原宽内容并排排布，右滑展开、左滑关闭，同步移动两个页面且不覆盖；`onPointerDown` 只观察按下位置，不消费事件，用于输入区外点击收起等交互

`AppMarkdownText` 使用 Markwon 4.6.2 原生渲染标题、强调、列表、引用、代码块、表格、删除线和链接，跟随 Material 配色与字号，支持文字选择，HTTP(S) 链接通过回调交给调用方导航，不使用 WebView 或执行 HTML

[component](.\src\main\kotlin\com\aster\core\design_system\component) 存放与业务无关的UI，如按钮、tag等

[modifier](./src/main/kotlin/com/aster/core/design_system/modifier) 存放通用的 Compose Modifier。`blurFadeEdges` 为当前内容添加边缘模糊渐隐，支持横向和纵向；`edgeSize` 默认 24dp，`fadeStart`、`fadeEnd` 分别控制逻辑首尾，横向自动适配 RTL。开关过渡为 150ms，Android 13+ 使用双遍高斯模糊，旧版本保留透明渐隐。应将它应用在需要渐隐的内容层，让固定控件和玻璃底板保持独立。

```kotlin
Modifier.blurFadeEdges(
    orientation = Orientation.Vertical,
    edgeSize = 24.dp,
    fadeStart = listState.canScrollBackward,
    fadeEnd = listState.canScrollForward
)
```
