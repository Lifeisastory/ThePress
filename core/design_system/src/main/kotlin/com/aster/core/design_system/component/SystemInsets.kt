package com.aster.core.design_system.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/** 当前窗口状态栏顶部高度（dp）；用于滚动内容的首部留白，不应作为列表视口的 padding */
@Composable
fun statusBarHeight(): Dp {
    val density = LocalDensity.current
    return with(density) { WindowInsets.statusBars.getTop(this).toDp() }
}

/** 当前窗口底部导航栏高度（dp），不含键盘；导航栏位于侧边或隐藏时可能为 0 */
@Composable
fun navigationBarHeight(): Dp {
    val density = LocalDensity.current
    return with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
}

/**
 * 背景延伸到系统栏后方，内容避让当前窗口的安全区
 *
 * Activity 需开启 enableEdgeToEdge；独立 Dialog 窗口也需开启边到边绘制并设置透明系统栏
 *
 * [windowInsets] 默认包含系统栏、刘海和键盘；底部弹窗可按需只选择 Bottom + Horizontal
 * windowInsetsPadding 在布局阶段读取实际 Insets，并自动消费已应用的部分，嵌套时不会重复避让
 * 内容无需再次添加 statusBarsPadding / navigationBarsPadding
 * 滚动页面若需让内容经过系统栏后方，应只在外层处理水平 Insets，
 * 将上下高度应用到列表 contentPadding；顶部控件与悬浮底栏自行避让对应安全区
 *
 * ModalBottomSheet 如交由本组件统一处理 Insets，应设置
 * contentWindowInsets = { WindowInsets(0, 0, 0, 0) }，并让 containerColor 与 [backgroundColor] 一致
 * 不应将主页面的内容留白传入弹窗；弹窗从自身窗口获取 Insets
 */
@Composable
fun SystemInsets(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(backgroundColor)
            .windowInsetsPadding(windowInsets)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColorFor(backgroundColor)) {
            content()
        }
    }
}
