package com.aster.feature.home.impl.model

import com.aster.core.model.NewsArticle

internal data class HomeUiState(
    val feeds: List<SourceFeedUiState> = emptyList(),
    val selectedSourceId: String? = null,
    val query: String = "",
    val searchResults: List<NewsArticle> = emptyList(),
    val isSearching: Boolean = false,
    val searchFailed: Boolean = false
) {
    val isRefreshing: Boolean get() = feeds.any { it.isLoading }
    val isSearchMode: Boolean get() = query.isNotBlank()
    val visibleFeeds: List<SourceFeedUiState>
        get() = feeds.filter { selectedSourceId == null || it.source.id == selectedSourceId }
    val visibleArticles: List<NewsArticle>
        get() = if (isSearchMode) searchResults else buildList {
            val visible = visibleFeeds
            repeat(visible.maxOfOrNull { it.articles.size } ?: 0) { index ->
                visible.forEach { feed -> feed.articles.getOrNull(index)?.let { add(it) } }
            }
        }
}
