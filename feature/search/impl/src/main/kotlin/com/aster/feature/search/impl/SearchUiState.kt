package com.aster.feature.search.impl

import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsSource

internal data class SearchUiState(
    val sources: List<NewsSource> = emptyList(),
    val query: String = "",
    val results: List<NewsArticle> = emptyList(),
    val isSearching: Boolean = false,
    val hasError: Boolean = false
)
