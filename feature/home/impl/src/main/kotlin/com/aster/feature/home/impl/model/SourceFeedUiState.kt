package com.aster.feature.home.impl.model

import com.aster.core.data.model.NewsArticle
import com.aster.core.data.model.NewsLoadResult
import com.aster.core.data.model.NewsSource

internal data class SourceFeedUiState(
    val source: NewsSource,
    val isLoading: Boolean = false,
    val articles: List<NewsArticle> = emptyList(),
    val error: NewsLoadResult.Failure? = null
)
