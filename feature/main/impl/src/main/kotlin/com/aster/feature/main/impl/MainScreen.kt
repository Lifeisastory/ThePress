package com.aster.feature.main.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.aster.core.data.repository.NewsRepository
import com.aster.core.data.repository.UserPreferencesRepository
import com.aster.core.design_system.component.SystemInsets
import com.aster.core.design_system.component.navigationBarHeight
import com.aster.core.design_system.component.statusBarHeight
import com.aster.core.design_system.component.liquid_glass.AppNavigationBar
import com.aster.core.design_system.component.liquid_glass.AppNavigationItem
import com.aster.feature.home.impl.HomePage
import com.aster.feature.home.impl.SourceOrderDialog
import com.aster.feature.home.impl.SourceOrderEditor
import com.aster.feature.me.impl.ProfilePage
import com.aster.feature.saved.impl.SavedPage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
internal fun MainScreen(
    repository: NewsRepository,
    preferencesRepository: UserPreferencesRepository,
    onOpenSearch: () -> Unit,
    onOpenArticle: (String) -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var homeScrollToTop by remember { mutableStateOf<(() -> Unit)?>(null) }
    var sourceOrderEditor by remember { mutableStateOf<SourceOrderEditor?>(null) }
    val savedPages = rememberSaveableStateHolder()
    val items = MainTab.entries.map { AppNavigationItem(stringResource(it.labelRes), it.icon) }
    val backgroundColor = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // 统一设计间距；页面避让高度单独计算，避免反馈到导航栏自身的测量
    val layoutPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
    var navigationBarHeightPx by remember { mutableIntStateOf(0) }
    val safePadding = WindowInsets.safeDrawing.asPaddingValues()
    val topInset = maxOf(statusBarHeight(), safePadding.calculateTopPadding())
    val bottomInset = maxOf(navigationBarHeight(), safePadding.calculateBottomPadding())
    // 实测高度包含玻璃栏外间距及底部系统安全区，取最大值避免重复累加
    val bottomContentInset = maxOf(with(density) { navigationBarHeightPx.toDp() }, bottomInset)
    val contentPadding = PaddingValues(
        start = layoutPadding.calculateStartPadding(layoutDirection),
        end = layoutPadding.calculateEndPadding(layoutDirection),
        top = topInset + layoutPadding.calculateTopPadding(),
        bottom = bottomContentInset + layoutPadding.calculateBottomPadding()
    )

    // 路由转场时 Main 可能仍在组合中，只有当前路由可以处理 Tab 返回
    BackHandler(enabled = isActive && selectedTab != MainTab.Home) {
        selectedTab = MainTab.Home
    }

    SystemInsets(
        modifier = modifier
            .fillMaxSize()
            .testTag("main_screen"),
        backgroundColor = backgroundColor,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    ) {
        // 仅录入页面内容，导航栏作为兄弟节点绘制；列表视口保持完整高度
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .then(if (sourceOrderEditor != null) Modifier.clearAndSetSemantics {} else Modifier)
        ) {
            savedPages.SaveableStateProvider(selectedTab) {
                when (selectedTab) {
                    MainTab.Home -> HomePage(
                        repository = repository,
                        preferencesRepository = preferencesRepository,
                        contentPadding = contentPadding,
                        onOpenSearch = onOpenSearch,
                        onOpenArticle = onOpenArticle,
                        onScrollToTopChanged = { homeScrollToTop = it },
                        onEditSourceOrder = { sourceOrderEditor = it }
                    )
                    MainTab.Saved -> SavedPage(contentPadding)
                    MainTab.Profile -> ProfilePage(
                        contentPadding = contentPadding,
                        repository = repository,
                        preferencesRepository = preferencesRepository,
                        onOpenLink = onOpenArticle
                    )
                }
            }
        }
        AppNavigationBar(
            items = items,
            selectedIndex = MainTab.entries.indexOf(selectedTab),
            onItemSelected = { selectedTab = MainTab.entries[it] },
            backdrop = backdrop,
            showScrollToTop = selectedTab == MainTab.Home && homeScrollToTop != null,
            onScrollToTop = { homeScrollToTop?.invoke() },
            modifier = Modifier
                .then(if (sourceOrderEditor != null) Modifier.clearAndSetSemantics {} else Modifier)
                .align(Alignment.BottomCenter)
                .onSizeChanged { navigationBarHeightPx = it.height }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(
                    start = layoutPadding.calculateStartPadding(layoutDirection),
                    end = layoutPadding.calculateEndPadding(layoutDirection),
                    top = layoutPadding.calculateBottomPadding(),
                    bottom = layoutPadding.calculateBottomPadding()
                )
        )
        sourceOrderEditor?.let { editor ->
            SourceOrderDialog(
                editor = editor,
                backdrop = backdrop,
                onDismiss = { sourceOrderEditor = null },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
