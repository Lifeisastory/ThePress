package com.aster.feature.home.impl.model

internal data class HomeUiState(val feeds: List<SourceFeedUiState> = emptyList()) {
    val isRefreshing: Boolean get() = feeds.any { it.isLoading }
}
