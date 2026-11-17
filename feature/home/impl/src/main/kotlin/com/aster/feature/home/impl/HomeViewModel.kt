package com.aster.feature.home.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsLoadResult
import com.aster.core.model.NewsSource
import com.aster.core.data.repository.NewsRepository
import com.aster.feature.home.impl.model.HomeUiState
import com.aster.feature.home.impl.model.SourceFeedUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class HomeViewModel(
    sources: List<NewsSource>,
    private val loadSource: suspend (String) -> NewsLoadResult,
    private val readCached: suspend (String) -> List<NewsArticle>? = { null },
    private val search: (String, String?) -> Flow<List<NewsArticle>> = { _, _ -> flowOf(emptyList()) }
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(sources.map { SourceFeedUiState(it, isLoading = true) }))
    val uiState = _uiState.asStateFlow()
    private val pending = linkedSetOf<String>()
    private val active = mutableSetOf<String>()
    private var searchJob: Job? = null
    private var cacheLoaded = false

    init {
        viewModelScope.launch {
            // 先恢复所有本地快照，再联网；过期数据在断网启动时仍可阅读
            sources.forEach { source ->
                readCached(source.id)?.let { articles ->
                    updateSource(source.id) { it.copy(articles = articles) }
                }
            }
            cacheLoaded = true
            refreshAll()
        }
    }

    fun selectSource(sourceId: String?) {
        if (sourceId != null && _uiState.value.feeds.none { it.source.id == sourceId }) return
        if (_uiState.value.selectedSourceId == sourceId) return
        _uiState.update { it.copy(selectedSourceId = sourceId) }
        restartSearch(debounce = false)
        // 不重复抓取已加载来源；排队中的当前来源会在下一个空闲槽位优先执行
        drainRequests()
    }

    fun setQuery(query: String) {
        if (_uiState.value.query == query) return
        _uiState.update { it.copy(query = query) }
        restartSearch(debounce = true)
    }

    private fun restartSearch(debounce: Boolean) {
        searchJob?.cancel()
        val query = _uiState.value.query.trim()
        val sourceId = _uiState.value.selectedSourceId
        _uiState.update {
            it.copy(searchResults = emptyList(), isSearching = query.isNotEmpty(), searchFailed = false)
        }
        if (query.isEmpty()) return
        searchJob = viewModelScope.launch {
            try {
                if (debounce) delay(300)
                search(query, sourceId).collect { articles ->
                    _uiState.update { it.copy(searchResults = articles, isSearching = false) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(isSearching = false, searchFailed = true) }
            }
        }
    }

    fun retrySearch() = restartSearch(debounce = false)

    fun refreshAll() {
        _uiState.value.feeds.forEach { enqueue(it.source.id) }
        drainRequests()
    }

    fun refresh(sourceId: String) {
        if (_uiState.value.feeds.none { it.source.id == sourceId }) return
        enqueue(sourceId)
        drainRequests()
    }

    private fun enqueue(sourceId: String) {
        if (sourceId in active || !pending.add(sourceId)) return
        updateSource(sourceId) { it.copy(isLoading = true, error = null) }
    }

    private fun drainRequests() {
        if (!cacheLoaded) return
        while (active.size < 4 && pending.isNotEmpty()) {
            val sourceId = _uiState.value.selectedSourceId?.takeIf { it in pending } ?: pending.first()
            pending.remove(sourceId)
            active.add(sourceId)
            viewModelScope.launch {
                try {
                    // 所有刷新都使用同一个 fetch，继续遵守仓库的十分钟缓存有效期
                    when (val result = loadSource(sourceId)) {
                        is NewsLoadResult.Success -> updateSource(sourceId) {
                            it.copy(isLoading = false, articles = result.articles, error = null)
                        }
                        is NewsLoadResult.Failure -> updateSource(sourceId) {
                            it.copy(isLoading = false, error = result)
                        }
                    }
                } finally {
                    active.remove(sourceId)
                }
                drainRequests()
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
                return modelClass.cast(HomeViewModel(
                    repository.sources, repository::fetch, repository::readCached, repository::search
                ))!!
            }
        }
    }
}
