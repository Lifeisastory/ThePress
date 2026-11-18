package com.aster.feature.home.impl.model

import com.aster.core.model.NewsArticle

internal data class HomeUiState(
    val feeds: List<SourceFeedUiState> = emptyList(),
    val selectedSourceId: String? = null,
    val isSourceOrderLoaded: Boolean = true
) {
    val isRefreshing: Boolean get() = feeds.any { it.isLoading }
    val visibleFeeds: List<SourceFeedUiState>
        get() = feeds.filter { selectedSourceId == null || it.source.id == selectedSourceId }
    val visibleArticles: List<NewsArticle>
        get() = buildList {
            val visible = visibleFeeds
            repeat(visible.maxOfOrNull { it.articles.size } ?: 0) { index ->
                visible.forEach { feed -> feed.articles.getOrNull(index)?.let { add(it) } }
            }
        }
}
