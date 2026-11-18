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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
internal fun HomeRoute(
    repository: NewsRepository,
    contentPadding: PaddingValues,
    onOpenSearch: () -> Unit
) {
    val factory = remember(repository) { HomeViewModel.factory(repository) }
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    HomeScreen(
        state = state,
        contentPadding = contentPadding,
        onRefreshAll = viewModel::refreshAll,
        onRefreshSource = viewModel::refresh,
        onSelectSource = viewModel::selectSource,
        onOpenSearch = onOpenSearch,
        onOpenArticle = { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: IllegalArgumentException) {
                Toast.makeText(context, R.string.home_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    )
}

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onRefreshAll: () -> Unit,
    onRefreshSource: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onSelectSource: (String?) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val direction = LocalLayoutDirection.current
    val start = contentPadding.calculateStartPadding(direction)
    val end = contentPadding.calculateEndPadding(direction)
    val selectedTab = state.feeds.indexOfFirst { it.source.id == state.selectedSourceId } + 1
    val allSourcesLabel = stringResource(R.string.home_all_sources)
    val tabs = listOf(AppTab("all", allSourcesLabel)) +
        state.feeds.map { AppTab(it.source.id, it.source.name) }
    val currentSourceName = state.feeds.firstOrNull { it.source.id == state.selectedSourceId }?.source?.name
        ?: allSourcesLabel
    val backgroundColor = MaterialTheme.colorScheme.background
    // 顶部玻璃控件只采样列表，避免将自身重复录入背景。
    val feedBackdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    Box(modifier.fillMaxSize().testTag("home_screen")) {
        HomeFeed(
            state = state,
            contentPadding = PaddingValues(
                start = start,
                end = end,
                top = with(density) { headerHeightPx.toDp() } + 16.dp,
                bottom = contentPadding.calculateBottomPadding()
            ),
            onRefreshAll = onRefreshAll,
            onRefreshSource = onRefreshSource,
            onOpenArticle = onOpenArticle,
            modifier = Modifier.fillMaxSize().layerBackdrop(feedBackdrop)
        )
        // 系统安全区由外层 SystemInsets 处理。
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
                sourceId = state.selectedSourceId,
                contentDescription = stringResource(R.string.home_current_source, currentSourceName),
                backdrop = feedBackdrop,
                modifier = Modifier.testTag("home_source_icon")
            )
            AppTabRow(
                tabs = tabs,
                selectedIndex = selectedTab,
                onTabSelected = { index -> onSelectSource(if (index == 0) null else state.feeds[index - 1].source.id) },
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
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(HomeUiState(), PaddingValues(24.dp), {}, {}, {}, {}, {})
    }
}
