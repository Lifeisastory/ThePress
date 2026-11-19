package com.aster.feature.for_you.impl

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aster.core.data.repository.NewsRepository
import com.aster.core.data.repository.UserPreferencesRepository
import com.aster.core.design_system.component.liquid_glass.AppIconButton
import com.aster.core.design_system.component.liquid_glass.AppTab
import com.aster.core.design_system.component.liquid_glass.AppTabRow
import com.aster.core.design_system.component.liquid_glass.AppIcon
import com.aster.feature.for_you.impl.model.ForYouUiState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
fun ForYouPage(
    repository: NewsRepository,
    preferencesRepository: UserPreferencesRepository,
    contentPadding: PaddingValues,
    onOpenSearch: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onScrollToTopChanged: ((() -> Unit)?) -> Unit = {},
    onEditSourceOrder: (SourceOrderEditor) -> Unit = {}
) {
    val factory = remember(repository, preferencesRepository) { ForYouViewModel.factory(repository, preferencesRepository) }
    val viewModel: ForYouViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ForYouPage(
        state = state,
        contentPadding = contentPadding,
        onRefreshAll = viewModel::refreshAll,
        onRefreshSource = viewModel::refresh,
        onSelectSource = viewModel::selectSource,
        onOpenSearch = onOpenSearch,
        onScrollToTopChanged = onScrollToTopChanged,
        onEditSourceOrder = {
            onEditSourceOrder(SourceOrderEditor(state.feeds.map { it.source }, viewModel::saveSourceOrder))
        },
        onOpenArticle = onOpenArticle
    )
}

@Composable
internal fun ForYouPage(
    state: ForYouUiState,
    contentPadding: PaddingValues,
    onRefreshAll: () -> Unit,
    onRefreshSource: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onSelectSource: (String?) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onScrollToTopChanged: ((() -> Unit)?) -> Unit = {},
    onEditSourceOrder: () -> Unit = {}
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
    val allSourcesLabel = stringResource(R.string.for_you_all_sources)
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
    LaunchedEffect(selectedTab, pageSourceIds, pagerState) {
        // 顺序改变后按选中来源 ID 定位，不能沿用旧页码
        if (pagerState.currentPage != selectedTab) {
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

    Box(modifier.fillMaxSize().testTag("for_you_page")) {
        HorizontalPager(
            state = pagerState,
            key = { page -> tabs[page].id },
            // 整个 Pager 统一采样，拖动时玻璃可同时映射正在切换的两页
            modifier = Modifier.fillMaxSize().layerBackdrop(feedBackdrop).testTag("for_you_source_pager")
        ) { page ->
            val sourceId = pageSourceIds[page]
            Box(Modifier.fillMaxSize().testTag("for_you_source_page_${sourceId ?: "all"}")) {
                ForYouFeed(
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
                .testTag("for_you_header"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                sourceId = visibleSourceId,
                contentDescription = stringResource(R.string.for_you_current_source, currentSourceName),
                backdrop = feedBackdrop,
                modifier = Modifier.testTag("for_you_source_icon")
                    .clickable(
                        enabled = state.isSourceOrderLoaded && state.feeds.isNotEmpty(),
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.for_you_open_source_order)
                    ) {
                        scope.launch {
                            // 打开前结束横向转场，以当前可见来源作为重排后的定位依据
                            pagerState.scrollToPage(visiblePage)
                            currentOnSelectSource(visibleSourceId)
                            onEditSourceOrder()
                        }
                    }
            )
            AppTabRow(
                tabs = tabs,
                pagerState = pagerState,
                onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                backdrop = feedBackdrop,
                modifier = Modifier.weight(1f).testTag("for_you_source_tabs")
            )
            AppIconButton(
                icon = Icons.Default.Search,
                contentDescription = stringResource(R.string.for_you_open_search),
                onClick = onOpenSearch,
                backdrop = feedBackdrop,
                modifier = Modifier.testTag("for_you_search_button")
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ForYouPagePreview() {
    MaterialTheme {
        ForYouPage(ForYouUiState(), PaddingValues(24.dp), {}, {}, {}, {}, {})
    }
}
