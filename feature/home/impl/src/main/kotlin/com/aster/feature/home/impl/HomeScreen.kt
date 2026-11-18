package com.aster.feature.home.impl

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.aster.core.design_system.component.liquid_glass.AppSearchBar
import com.aster.feature.home.impl.model.HomeUiState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
internal fun HomeRoute(repository: NewsRepository, contentPadding: PaddingValues) {
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
        onQueryChange = viewModel::setQuery,
        onSelectSource = viewModel::selectSource,
        onRetrySearch = viewModel::retrySearch,
        onOpenArticle = { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: IllegalArgumentException) {
                Toast.makeText(context, R.string.home_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onRefreshAll: () -> Unit,
    onRefreshSource: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectSource: (String?) -> Unit,
    onRetrySearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val direction = LocalLayoutDirection.current
    val start = contentPadding.calculateStartPadding(direction)
    val end = contentPadding.calculateEndPadding(direction)
    val selectedTab = state.feeds.indexOfFirst { it.source.id == state.selectedSourceId } + 1
    val backgroundColor = MaterialTheme.colorScheme.background
    // 搜索框只采样列表，不能采样包含自身的整页，避免玻璃层递归录制
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
            onRetrySearch = onRetrySearch,
            modifier = Modifier.fillMaxSize().layerBackdrop(feedBackdrop)
        )
        // 页面只处理自己的顶部控件，系统栏与键盘由外层 AppImmersiveBox 避让
        Column(
            Modifier
                .onSizeChanged { headerHeightPx = it.height }
                .padding(start = start, end = end, top = contentPadding.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppSearchBar(
                value = state.query,
                onValueChange = onQueryChange,
                backdrop = feedBackdrop,
                modifier = Modifier.fillMaxWidth().testTag("home_search"),
                placeholder = { Text(stringResource(R.string.home_search_hint)) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    { TextButton(onClick = { onQueryChange("") }) { Text(stringResource(R.string.home_clear_search)) } }
                } else null
            )
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth().testTag("home_source_tabs"),
                edgePadding = 0.dp
            ) {
                Tab(
                    selected = state.selectedSourceId == null,
                    onClick = { onSelectSource(null) },
                    modifier = Modifier.testTag("home_tab_all"),
                    text = { Text(stringResource(R.string.home_all_sources)) }
                )
                state.feeds.forEach { feed ->
                    Tab(
                        selected = state.selectedSourceId == feed.source.id,
                        onClick = { onSelectSource(feed.source.id) },
                        modifier = Modifier.testTag("home_tab_${feed.source.id}"),
                        text = { Text(feed.source.name, maxLines = 1) }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(HomeUiState(), PaddingValues(24.dp), {}, {}, {}, {}, {}, {})
    }
}
