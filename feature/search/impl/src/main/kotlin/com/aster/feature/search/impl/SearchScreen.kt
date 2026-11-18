package com.aster.feature.search.impl

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aster.core.data.repository.NewsRepository
import com.aster.core.design_system.component.SystemInsets
import com.aster.core.design_system.component.navigationBarHeight
import com.aster.core.design_system.component.statusBarHeight
import com.aster.core.design_system.component.liquid_glass.AppIconButton
import com.aster.core.design_system.component.liquid_glass.AppSearchBar
import com.aster.core.ui.NewsArticleCard
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
internal fun SearchRoute(
    repository: NewsRepository,
    onBack: () -> Unit
) {
    val factory = remember(repository) { SearchViewModel.factory(repository) }
    val viewModel: SearchViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    SearchScreen(
        state = state,
        onQueryChange = viewModel::setQuery,
        onSearch = viewModel::submitSearch,
        onBack = onBack,
        onOpenArticle = { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: IllegalArgumentException) {
                Toast.makeText(context, R.string.search_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    )
}

@Composable
internal fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 独立路由只考虑自身的系统栏和键盘，不预留 Main 的玻璃导航栏空间
    val safePadding = WindowInsets.safeDrawing.asPaddingValues()
    val contentPadding = PaddingValues(
        start = 24.dp,
        end = 24.dp,
        top = maxOf(statusBarHeight(), safePadding.calculateTopPadding()) + 24.dp,
        bottom = maxOf(navigationBarHeight(), safePadding.calculateBottomPadding()) + 16.dp
    )
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    val start = contentPadding.calculateStartPadding(direction)
    val end = contentPadding.calculateEndPadding(direction)
    val background = MaterialTheme.colorScheme.background
    val resultsBackdrop = rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val sourcesById = remember(state.sources) { state.sources.associateBy { it.id } }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(state.query) { listState.scrollToItem(0) }

    SystemInsets(
        modifier = modifier.fillMaxSize().testTag("search_screen"),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().layerBackdrop(resultsBackdrop).testTag("search_results"),
            contentPadding = PaddingValues(
                start = start,
                end = end,
                top = with(density) { headerHeightPx.toDp() } + 16.dp,
                bottom = contentPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "status") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        state.query.isBlank() -> Text(
                            stringResource(R.string.search_start_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> {
                            Text(
                                stringResource(R.string.search_count, state.results.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (state.hasError) {
                                Text(stringResource(R.string.search_failed), color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = onSearch) { Text(stringResource(R.string.search_retry)) }
                            } else if (!state.isSearching && state.results.isEmpty()) {
                                Text(stringResource(R.string.search_empty))
                            }
                        }
                    }
                }
            }
            items(state.results, key = { "${it.sourceId}:${it.id}" }) { article ->
                NewsArticleCard(
                    article = article,
                    source = sourcesById.getValue(article.sourceId),
                    onClick = { onOpenArticle(article.url) },
                    modifier = Modifier.testTag("search_article_${article.sourceId}_${article.id}")
                )
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
                .padding(start = start, end = end, top = contentPadding.calculateTopPadding()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.search_back),
                onClick = {
                    focusManager.clearFocus()
                    onBack()
                },
                backdrop = resultsBackdrop,
                modifier = Modifier.testTag("search_back")
            )
            AppSearchBar(
                value = state.query,
                onValueChange = onQueryChange,
                backdrop = resultsBackdrop,
                modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("search_input"),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    { TextButton(onClick = { onQueryChange("") }) { Text(stringResource(R.string.search_clear)) } }
                } else null,
                onSearch = { onSearch() }
            )
        }
    }
}
