package com.aster.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aster.core.data.model.NewsArticle
import com.aster.core.data.model.NewsFailureReason
import com.aster.core.data.model.NewsLoadResult
import com.aster.feature.home.impl.model.HomeUiState
import com.aster.feature.home.impl.model.SourceFeedUiState

internal fun LazyListScope.homeFeedItems(
    state: HomeUiState,
    onRefreshSource: (String) -> Unit,
    onOpenArticle: (String) -> Unit
) {
    items(state.feeds, key = { "source:${it.source.id}" }) { feed ->
        SourceStatusCard(feed, onRefresh = { onRefreshSource(feed.source.id) })
    }
    // 交替展示两个榜单，保留各自顺序，让首屏附近就能看到两个来源的内容
    val articles = buildList {
        repeat(state.feeds.maxOfOrNull { it.articles.size } ?: 0) { index ->
            state.feeds.forEach { feed ->
                feed.articles.getOrNull(index)?.let { add(feed.source to it) }
            }
        }
    }
    items(articles, key = { (source, article) -> "article:${source.id}:${article.id}" }) { (source, article) ->
        NewsArticleCard(article, source.name, onOpen = { onOpenArticle(article.url) })
    }
}

@Composable
private fun SourceStatusCard(feed: SourceFeedUiState, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_source_${feed.source.id}")
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = feed.source.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onRefresh, enabled = !feed.isLoading) {
                Text(stringResource(if (feed.error == null) R.string.home_refresh_source else R.string.home_retry))
            }
        }
        Text(
            text = when {
                feed.isLoading -> stringResource(R.string.home_loading)
                feed.error != null && feed.articles.isNotEmpty() ->
                    stringResource(R.string.home_kept_articles, feed.articles.size)
                feed.error != null -> stringResource(R.string.home_load_failed)
                feed.articles.isEmpty() -> stringResource(R.string.home_empty)
                else -> stringResource(R.string.home_article_count, feed.articles.size)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (feed.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        feed.error?.let { error ->
            Text(
                text = failureMessage(error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun NewsArticleCard(article: NewsArticle, sourceName: String, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = sourceName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = article.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        article.summary?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.home_open_article))
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
