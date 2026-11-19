package com.aster.feature.for_you.impl.model

import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsRefreshResult
import com.aster.core.model.NewsSource

internal data class SourceFeedUiState(
    val source: NewsSource,
    val isLoading: Boolean = false,
    val articles: List<NewsArticle> = emptyList(),
    val error: NewsRefreshResult.Failure? = null
)
