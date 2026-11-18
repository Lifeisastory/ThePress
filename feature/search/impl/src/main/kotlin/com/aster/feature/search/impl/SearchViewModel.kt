package com.aster.feature.search.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aster.core.data.repository.NewsRepository
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 独立搜索页只订阅全部来源的本地查询，不接收首页来源，也不触发数据抓取。 */
internal class SearchViewModel(
    sources: List<NewsSource>,
    private val search: (String) -> Flow<List<NewsArticle>>
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SearchUiState(sources = sources)
    )
    val uiState = _uiState.asStateFlow()
    private var searchJob: Job? = null

    fun setQuery(query: String) {
        if (_uiState.value.query == query) return
        _uiState.update { it.copy(query = query) }
        restartSearch(debounce = true)
    }

    fun submitSearch() = restartSearch(debounce = false)

    private fun restartSearch(debounce: Boolean) {
        searchJob?.cancel()
        val query = _uiState.value.query.trim()
        _uiState.update { it.copy(results = emptyList(), isSearching = query.isNotEmpty(), hasError = false) }
        if (query.isEmpty()) return
        searchJob = viewModelScope.launch {
            try {
                if (debounce) delay(300)
                search(query).collect { articles ->
                    _uiState.update { it.copy(results = articles, isSearching = false) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(isSearching = false, hasError = true) }
            }
        }
    }

    companion object {
        fun factory(repository: NewsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == SearchViewModel::class.java)
                    return modelClass.cast(SearchViewModel(repository.sources) { query ->
                        repository.search(query, sourceId = null)
                    })!!
                }
            }
    }
}
