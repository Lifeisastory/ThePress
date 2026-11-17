package com.aster.core.model

sealed interface NewsLoadResult {
    data class Success(val articles: List<NewsArticle>) : NewsLoadResult

    data class Failure(val reason: NewsFailureReason, val httpCode: Int? = null) : NewsLoadResult
}
