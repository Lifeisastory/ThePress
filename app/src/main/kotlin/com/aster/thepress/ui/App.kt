package com.aster.thepress.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aster.core.data.repository.NewsRepository
import com.aster.core.design_system.component.liquid_glass.AppNavigationBar
import com.aster.core.design_system.component.liquid_glass.AppNavigationItem
import com.aster.core.design_system.component.SystemInsets
import com.aster.core.design_system.component.navigationBarHeight
import com.aster.core.design_system.component.statusBarHeight
import com.aster.thepress.navigation.AppNavHost
import com.aster.thepress.navigation.TOP_LEVEL_NAV_ITEMS
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
internal fun App(
    repository: NewsRepository,
    modifier: Modifier = Modifier,
    appState: AppState = rememberAppState()
) {
    val keys = TOP_LEVEL_NAV_ITEMS.keys.toList()
    val items = TOP_LEVEL_NAV_ITEMS.values.map {
        AppNavigationItem(
            stringResource(it.labelRes),
            it.icon
        )
    }
    val selectedIndex = keys.indexOf(appState.navigationState.currentTopLevelKey)
    val showNavigationBar = appState.navigationState.currentKey in keys
    val backgroundColor = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val density = LocalDensity.current
    var navigationBarHeightPx by remember { mutableIntStateOf(0) }
    val safePadding = WindowInsets.safeDrawing.asPaddingValues()
    // 系统栏高度之外也照顾刘海、窗口标题栏与键盘；重叠区域取最大值，避免重复累加
    val topInset = maxOf(statusBarHeight(), safePadding.calculateTopPadding())
    val bottomInset = maxOf(navigationBarHeight(), safePadding.calculateBottomPadding())
    val bottomContentInset = if (showNavigationBar) {
        maxOf(with(density) { navigationBarHeightPx.toDp() }, bottomInset)
    } else bottomInset
    SystemInsets(
        modifier = modifier.fillMaxSize(),
        backgroundColor = backgroundColor,
        // 列表保持完整窗口高度，顶部和底部安全区由页面内容留白承担
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    ) {
        // 记录导航宿主中的页面内容，玻璃栏仍作为独立的兄弟节点绘制
        AppNavHost(
            appState = appState,
            repository = repository,
            // 页面用此值偏移固定控件，并设置滚动列表的 contentPadding，不裁剪列表视口
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = topInset + 24.dp,
                bottom = bottomContentInset + 16.dp
            ),
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        )
        if (showNavigationBar) AppNavigationBar(
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = { appState.navigator.navigate(keys[it]) },
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { navigationBarHeightPx = it.height }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 420.dp)
        )
    }
}
