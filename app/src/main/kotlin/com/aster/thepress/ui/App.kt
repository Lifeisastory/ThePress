package com.aster.thepress.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
    SystemInsets(
        modifier = modifier.fillMaxSize(),
        backgroundColor = backgroundColor
    ) {
        // 记录导航宿主中的页面内容，玻璃栏仍作为独立的兄弟节点绘制
        AppNavHost(
            appState = appState,
            repository = repository,
            // 系统安全区由外层统一避让，这里只预留页面间距和悬浮导航栏的实际高度
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 24.dp,
                bottom = (if (showNavigationBar) with(density) { navigationBarHeightPx.toDp() } else 0.dp) + 16.dp
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 420.dp)
        )
    }
}
