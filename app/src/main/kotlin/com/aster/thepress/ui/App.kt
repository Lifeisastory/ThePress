package com.aster.thepress.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aster.core.data.repository.NewsRepository
import com.aster.core.design_system.component.AppNavigationBar
import com.aster.core.design_system.component.AppNavigationItem
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
    val items = TOP_LEVEL_NAV_ITEMS.values.map { AppNavigationItem(stringResource(it.labelRes), it.icon) }
    val selectedIndex = keys.indexOf(appState.navigationState.currentTopLevelKey)
    val backgroundColor = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val systemPadding = WindowInsets.safeDrawing.asPaddingValues()
    val density = LocalDensity.current
    var navigationBarHeight by remember { mutableStateOf(104.dp) }

    Box(modifier.fillMaxSize().background(backgroundColor)) {
        // 记录导航宿主中的页面内容，玻璃栏仍作为独立的兄弟节点绘制
        AppNavHost(
            appState = appState,
            repository = repository,
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = systemPadding.calculateTopPadding() + 24.dp,
                bottom = navigationBarHeight + 16.dp
            ),
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        )

        AppNavigationBar(
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = { appState.navigator.navigate(keys[it]) },
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { navigationBarHeight = with(density) { it.height.toDp() } }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 420.dp)
        )
    }
}
