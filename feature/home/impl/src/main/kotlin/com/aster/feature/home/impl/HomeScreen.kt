package com.aster.feature.home.impl

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aster.core.data.repository.NewsRepository
import com.aster.feature.home.impl.model.HomeUiState

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
    Column(
        modifier.fillMaxSize().testTag("home_screen").padding(top = contentPadding.calculateTopPadding())
    ) {
        Column(
            Modifier.padding(start = start, end = end, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().testTag("home_search"),
                placeholder = { Text(stringResource(R.string.home_search_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                trailingIcon = if (state.query.isNotEmpty()) {
                    { TextButton(onClick = { onQueryChange("") }) { Text(stringResource(R.string.home_clear_search)) } }
                } else null
            )
        }
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth().testTag("home_source_tabs"),
            edgePadding = start
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
        HomeFeed(
            state = state,
            contentPadding = PaddingValues(
                start = start, end = end, top = 16.dp, bottom = contentPadding.calculateBottomPadding()
            ),
            onRefreshAll = onRefreshAll,
            onRefreshSource = onRefreshSource,
            onOpenArticle = onOpenArticle,
            onRetrySearch = onRetrySearch,
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(HomeUiState(), PaddingValues(24.dp), {}, {}, {}, {}, {}, {})
    }
}
