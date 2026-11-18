package com.aster.feature.home.impl

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aster.core.data.repository.NewsRepository
import com.aster.core.design_system.component.liquid_glass.AppIconButton
import com.aster.core.design_system.component.liquid_glass.AppTab
import com.aster.core.design_system.component.liquid_glass.AppTabRow
import com.aster.core.design_system.component.liquid_glass.AppIcon
import com.aster.feature.home.impl.model.HomeUiState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import com.aster.core.ui.R as UiR

@Composable
fun HomePage(
    repository: NewsRepository,
    contentPadding: PaddingValues,
    onOpenSearch: () -> Unit,
    onScrollToTopChanged: ((() -> Unit)?) -> Unit = {}
) {
    val factory = remember(repository) { HomeViewModel.factory(repository) }
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    HomePage(
        state = state,
        contentPadding = contentPadding,
        onRefreshAll = viewModel::refreshAll,
        onRefreshSource = viewModel::refresh,
        onSelectSource = viewModel::selectSource,
        onOpenSearch = onOpenSearch,
        onScrollToTopChanged = onScrollToTopChanged,
        onOpenArticle = { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: IllegalArgumentException) {
                Toast.makeText(context, UiR.string.ui_article_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    )
}

@Composable
internal fun HomePage(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onRefreshAll: () -> Unit,
    onRefreshSource: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onSelectSource: (String?) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onScrollToTopChanged: ((() -> Unit)?) -> Unit = {}
) {
    val currentOnScrollToTopChanged by rememberUpdatedState(onScrollToTopChanged)
    DisposableEffect(Unit) {
        onDispose { currentOnScrollToTopChanged(null) }
    }
    val direction = LocalLayoutDirection.current
    val start = contentPadding.calculateStartPadding(direction)
    val end = contentPadding.calculateEndPadding(direction)
    val selectedTab = state.feeds.indexOfFirst { it.source.id == state.selectedSourceId } + 1
    val pageSourceIds = listOf<String?>(null) + state.feeds.map { it.source.id }
    val pagerState = rememberPagerState(initialPage = selectedTab, pageCount = { pageSourceIds.size })
    val scope = rememberCoroutineScope()
    val currentOnSelectSource by rememberUpdatedState(onSelectSource)
    val visiblePage = pagerState.currentPage.coerceIn(pageSourceIds.indices)
    val visibleSourceId = pageSourceIds[visiblePage]
    val allSourcesLabel = stringResource(R.string.home_all_sources)
    val tabs = listOf(AppTab("all", allSourcesLabel)) +
        state.feeds.map { AppTab(it.source.id, it.source.name) }
    val currentSourceName = state.feeds.firstOrNull { it.source.id == visibleSourceId }?.source?.name
        ?: allSourcesLabel
    val backgroundColor = MaterialTheme.colorScheme.background
    // 顶部玻璃控件只采样列表，避免将自身重复录入背景
    val feedBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }

    // 外部状态恢复时同步页面；Tab 点击直接驱动 Pager，避免双向动画互相打断
    LaunchedEffect(selectedTab, pagerState) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != selectedTab) {
            pagerState.scrollToPage(selectedTab)
        }
    }
    LaunchedEffect(pagerState, pageSourceIds) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { page ->
                if (page in pageSourceIds.indices) currentOnSelectSource(pageSourceIds[page])
            }
    }

    Box(modifier.fillMaxSize().testTag("home_page")) {
        HorizontalPager(
            state = pagerState,
            key = { page -> tabs[page].id },
            // 整个 Pager 统一采样，拖动时玻璃可同时映射正在切换的两页
            modifier = Modifier.fillMaxSize().layerBackdrop(feedBackdrop).testTag("home_source_pager")
        ) { page ->
            val sourceId = pageSourceIds[page]
            Box(Modifier.fillMaxSize().testTag("home_source_page_${sourceId ?: "all"}")) {
                HomeFeed(
                    state = state.copy(selectedSourceId = sourceId),
                    contentPadding = PaddingValues(
                        start = start,
                        end = end,
                        top = with(density) { headerHeightPx.toDp() } + 16.dp,
                        bottom = contentPadding.calculateBottomPadding()
                    ),
                    onRefreshAll = onRefreshAll,
                    onRefreshSource = onRefreshSource,
                    onOpenArticle = onOpenArticle,
                    modifier = Modifier.fillMaxSize(),
                    refreshEnabled = page == visiblePage && !pagerState.isScrollInProgress,
                    onScrollToTopChanged = { action ->
                        // Pager 会预组合相邻来源，只有当前可见页能更新底栏动作
                        if (page == visiblePage) currentOnScrollToTopChanged(action)
                    }
                )
            }
        }
        // 只偏移固定控件；其高度（含状态栏留白）计入列表首部，不缩短 Pager 的视口
        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
                .padding(start = start, end = end, top = contentPadding.calculateTopPadding())
                .testTag("home_header"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                sourceId = visibleSourceId,
                contentDescription = stringResource(R.string.home_current_source, currentSourceName),
                backdrop = feedBackdrop,
                modifier = Modifier.testTag("home_source_icon")
            )
            AppTabRow(
                tabs = tabs,
                selectedIndex = visiblePage,
                onTabSelected = { index -> scope.launch { pagerState.scrollToPage(index) } },
                backdrop = feedBackdrop,
                modifier = Modifier.weight(1f).testTag("home_source_tabs")
            )
            AppIconButton(
                icon = Icons.Default.Search,
                contentDescription = stringResource(R.string.home_open_search),
                onClick = onOpenSearch,
                backdrop = feedBackdrop,
                modifier = Modifier.testTag("home_search_button")
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePagePreview() {
    MaterialTheme {
        HomePage(HomeUiState(), PaddingValues(24.dp), {}, {}, {}, {}, {})
    }
}
