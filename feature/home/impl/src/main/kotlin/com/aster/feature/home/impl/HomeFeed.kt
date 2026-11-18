package com.aster.feature.home.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aster.core.model.NewsFailureReason
import com.aster.core.model.NewsLoadResult
import com.aster.core.ui.NewsArticleCard
import com.aster.feature.home.impl.model.HomeUiState

@Composable
internal fun HomeFeed(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onRefreshAll: () -> Unit,
    onRefreshSource: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val savedLists = rememberSaveableStateHolder()
    val sourceKey = state.selectedSourceId ?: "all"
    // 各来源独立保存位置，进入搜索页不会改动首页列表状态
    savedLists.SaveableStateProvider(sourceKey) {
        val listState = rememberLazyListState()
        val articles = state.visibleArticles
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize().testTag("home_feed"),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "status") {
                FeedStatus(state, onRefreshAll, onRefreshSource)
            }
            items(articles, key = { "article:${it.sourceId}:${it.id}" }) { article ->
                NewsArticleCard(
                    article = article,
                    source = state.feeds.first { it.source.id == article.sourceId }.source,
                    onClick = { onOpenArticle(article.url) },
                    modifier = Modifier.testTag("home_article_${article.sourceId}_${article.id}")
                )
            }
        }
    }
}

@Composable
private fun FeedStatus(
    state: HomeUiState,
    onRefreshAll: () -> Unit,
    onRefreshSource: (String) -> Unit
) {
    val feeds = state.visibleFeeds
    val loading = feeds.any { it.isLoading }
    val failures = feeds.filter { it.error != null }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_article_count, state.visibleArticles.size),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    state.selectedSourceId?.let(onRefreshSource) ?: onRefreshAll()
                },
                enabled = !loading,
                modifier = Modifier.testTag("home_refresh")
            ) {
                Text(stringResource(if (state.selectedSourceId == null) R.string.home_refresh_all else R.string.home_refresh_source))
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.selectedSourceId != null) {
            val feed = feeds.firstOrNull()
            feed?.error?.let { error ->
                Text(failureMessage(error), color = MaterialTheme.colorScheme.error)
                if (feed.articles.isNotEmpty()) Text(stringResource(R.string.home_kept_articles, feed.articles.size))
                TextButton(onClick = { onRefreshSource(feed.source.id) }, enabled = !feed.isLoading) {
                    Text(stringResource(R.string.home_retry))
                }
            }
        } else if (failures.isNotEmpty()) {
            Text(
                stringResource(R.string.home_failed_sources, failures.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (!loading && state.visibleArticles.isEmpty() && failures.isEmpty()) {
            Text(stringResource(R.string.home_empty))
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
