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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
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
import com.aster.core.design_system.modifier.onPointerDown
import com.aster.core.design_system.component.liquid_glass.AppNavigationItem
import com.aster.feature.for_you.impl.ForYouPage
import com.aster.feature.for_you.impl.SourceOrderDialog
import com.aster.feature.for_you.impl.SourceOrderEditor
import com.aster.feature.me.impl.ProfilePage
import com.aster.feature.digest.impl.DigestPage
import com.aster.core.data.repository.DigestSettingsRepository
import com.aster.core.data.repository.DigestAgentRepository
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
internal fun MainScreen(
    repository: NewsRepository,
    preferencesRepository: UserPreferencesRepository,
    digestSettingsRepository: DigestSettingsRepository,
    digestAgentRepository: DigestAgentRepository,
    onOpenSearch: () -> Unit,
    onOpenArticle: (String) -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.ForYou) }
    var forYouScrollToTop by remember { mutableStateOf<(() -> Unit)?>(null) }
    var sourceOrderEditor by remember { mutableStateOf<SourceOrderEditor?>(null) }
    var digestOverlay by remember { mutableStateOf(false) }
    var inputDismissRequest by remember { mutableIntStateOf(0) }
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
    val safePadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val topInset = maxOf(statusBarHeight(), safePadding.calculateTopPadding())
    val bottomInset = maxOf(navigationBarHeight(), safePadding.calculateBottomPadding())
    val editingDigest = selectedTab == MainTab.Digest && WindowInsets.ime.getBottom(density) > 0
    // 实测高度包含玻璃栏外间距及底部系统安全区，取最大值避免重复累加
    // 简报页通过 imePadding 消费键盘 Insets，此处只传系统栏和导航栏的留白
    val bottomContentInset = if (editingDigest) 0.dp else maxOf(with(density) { navigationBarHeightPx.toDp() }, bottomInset)
    val contentPadding = PaddingValues(
        start = layoutPadding.calculateStartPadding(layoutDirection),
        end = layoutPadding.calculateEndPadding(layoutDirection),
        top = topInset + layoutPadding.calculateTopPadding(),
        bottom = bottomContentInset + layoutPadding.calculateBottomPadding()
    )

    // 路由转场时 Main 可能仍在组合中，只有当前路由可以处理 Tab 返回
    BackHandler(enabled = isActive && selectedTab != MainTab.ForYou) {
        selectedTab = MainTab.ForYou
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
                    MainTab.ForYou -> ForYouPage(
                        repository = repository,
                        preferencesRepository = preferencesRepository,
                        contentPadding = contentPadding,
                        onOpenSearch = onOpenSearch,
                        onOpenArticle = onOpenArticle,
                        onScrollToTopChanged = { forYouScrollToTop = it },
                        onEditSourceOrder = { sourceOrderEditor = it }
                    )
                    MainTab.Digest -> DigestPage(contentPadding, digestSettingsRepository, digestAgentRepository,
                        repository.sources, onOpenArticle,
                        onOverlayChanged = { digestOverlay = it }, inputDismissRequest = inputDismissRequest)
                    MainTab.Profile -> ProfilePage(
                        contentPadding = contentPadding,
                        repository = repository,
                        preferencesRepository = preferencesRepository,
                        onOpenLink = onOpenArticle
                    )
                }
            }
        }
        if (!editingDigest && !digestOverlay) AppNavigationBar(
            items = items,
            selectedIndex = MainTab.entries.indexOf(selectedTab),
            onItemSelected = { selectedTab = MainTab.entries[it] },
            backdrop = backdrop,
            showScrollToTop = selectedTab == MainTab.ForYou && forYouScrollToTop != null,
            onScrollToTop = { forYouScrollToTop?.invoke() },
            modifier = Modifier
                .then(if (sourceOrderEditor != null) Modifier.clearAndSetSemantics {} else Modifier)
                .align(Alignment.BottomCenter)
                .onPointerDown { inputDismissRequest++ }
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
