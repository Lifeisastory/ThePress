package com.aster.feature.home.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aster.core.model.NewsFailureReason
import com.aster.core.model.NewsLoadResult
import com.aster.core.design_system.component.AppRefreshLayout
import com.aster.core.ui.NewsArticleCard
import com.aster.feature.home.impl.model.HomeUiState

@Composable
internal fun HomeFeed(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onRefreshAll: () -> Unit,
    onRefreshSource: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
    refreshEnabled: Boolean = true
) {
    val savedLists = rememberSaveableStateHolder()
    val sourceKey = state.selectedSourceId ?: "all"
    // 各来源独立保存位置，进入搜索页不会改动首页列表状态
    savedLists.SaveableStateProvider(sourceKey) {
        val listState = rememberLazyListState()
        val articles = state.visibleArticles
        val feeds = state.visibleFeeds
        val loading = feeds.any { it.isLoading }
        val showStatus = feeds.any { it.error != null } || (!loading && articles.isEmpty())
        var statusDismissed by rememberSaveable { mutableStateOf(false) }
        // 每个来源独立关闭本次提示；提示清除或手动刷新后允许再次显示。
        LaunchedEffect(showStatus) {
            if (!showStatus) statusDismissed = false
        }
        AppRefreshLayout(
            scrollState = listState,
            isRefreshing = loading,
            isLoadingMore = false,
            // 当前来源仅提供整榜快照，没有统一分页能力
            hasMore = false,
            onRefresh = {
                statusDismissed = false
                state.selectedSourceId?.let(onRefreshSource) ?: onRefreshAll()
            },
            onLoadMore = {},
            modifier = modifier
                .fillMaxSize()
                .testTag("home_refresh_layout"),
            enabled = refreshEnabled,
            indicatorPadding = contentPadding
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_feed"),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showStatus && !statusDismissed) {
                    item(key = "status") {
                        FeedStatus(
                            state = state,
                            onDismiss = { statusDismissed = true },
                            modifier = Modifier.animateItem().testTag("home_feed_status")
                        )
                    }
                }
                itemsIndexed(
                    articles,
                    key = { _, article -> "article:${article.sourceId}:${article.id}" }) { index, article ->
                    NewsArticleCard(
                        article = article,
                        source = state.feeds.first { it.source.id == article.sourceId }.source,
                        onClick = { onOpenArticle(article.url) },
                        modifier = Modifier
                            .animateItem(fadeInSpec = null, fadeOutSpec = null)
                            .testTag("home_article_${article.sourceId}_${article.id}"),
                        showSourceName = state.selectedSourceId == null,
                        rank = if (state.selectedSourceId != null) index + 1 else null
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedStatus(
    state: HomeUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val feeds = state.visibleFeeds
    val loading = feeds.any { it.isLoading }
    val failures = feeds.filter { it.error != null }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.selectedSourceId != null) {
                val feed = feeds.firstOrNull()
                feed?.error?.let { error ->
                    Text(
                        failureMessage(error), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (feed.articles.isNotEmpty()) Text(
                        stringResource(R.string.home_kept_articles, feed.articles.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else if (failures.isNotEmpty()) {
                Text(
                    stringResource(R.string.home_failed_sources, failures.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (!loading && state.visibleArticles.isEmpty() && failures.isEmpty()) {
                Text(
                    stringResource(R.string.home_empty),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(32.dp).testTag("home_status_dismiss")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.home_dismiss_status),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun failureMessage(error: NewsLoadResult.Failure): String = when (error.reason) {
    NewsFailureReason.CONNECTION -> stringResource(R.string.home_connection_error)
    NewsFailureReason.TIMEOUT -> stringResource(R.string.home_timeout_error)
    NewsFailureReason.HTTP -> stringResource(R.string.home_http_error, error.httpCode ?: 0)
    NewsFailureReason.INVALID_DATA -> stringResource(R.string.home_data_error)
}
