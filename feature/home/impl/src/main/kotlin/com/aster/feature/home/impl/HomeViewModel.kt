package com.aster.feature.home.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aster.core.data.model.NewsLoadResult
import com.aster.core.data.model.NewsSource
import com.aster.core.data.repository.NewsRepository
import com.aster.feature.home.impl.model.HomeUiState
import com.aster.feature.home.impl.model.SourceFeedUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class HomeViewModel(
    sources: List<NewsSource>,
    private val loadSource: suspend (String) -> NewsLoadResult
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(sources.map { SourceFeedUiState(it) }))
    val uiState = _uiState.asStateFlow()
    private val requests = mutableMapOf<String, Job>()

    init {
        refreshAll()
    }

    fun refreshAll() {
        _uiState.value.feeds.forEach { refresh(it.source.id) }
    }

    fun refresh(sourceId: String) {
        if (_uiState.value.feeds.none { it.source.id == sourceId } || requests[sourceId]?.isActive == true) return
        requests[sourceId] = viewModelScope.launch {
            updateSource(sourceId) { it.copy(isLoading = true, error = null) }
            when (val result = loadSource(sourceId)) {
                is NewsLoadResult.Success -> updateSource(sourceId) {
                    it.copy(isLoading = false, articles = result.articles, error = null)
                }
                is NewsLoadResult.Failure -> updateSource(sourceId) {
                    // 刷新失败时保留已经展示的内容，两个来源分别反馈结果
                    it.copy(isLoading = false, error = result)
                }
            }
        }
    }

    private fun updateSource(sourceId: String, transform: (SourceFeedUiState) -> SourceFeedUiState) {
        _uiState.update { state ->
            state.copy(feeds = state.feeds.map { if (it.source.id == sourceId) transform(it) else it })
        }
    }

    companion object {
        fun factory(repository: NewsRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == HomeViewModel::class.java)
                return modelClass.cast(HomeViewModel(repository.sources, repository::fetch))!!
            }
        }
    }
}
