[](https://)

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
