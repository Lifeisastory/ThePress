package com.aster.feature.home.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aster.core.data.repository.NewsRepository
import com.aster.core.data.repository.UserPreferencesRepository
import com.aster.core.model.NewsFailureReason
import com.aster.core.model.NewsRefreshResult
import com.aster.feature.home.impl.model.HomeUiState
import com.aster.feature.home.impl.model.SourceFeedUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

internal class HomeViewModel(
    private val repository: NewsRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {
    private val defaultSourceIds = repository.sources.map { it.id }
    private val _uiState = MutableStateFlow(HomeUiState(
        feeds = repository.sources.map { SourceFeedUiState(it, isLoading = true) },
        isSourceOrderLoaded = false
    ))
    val uiState = _uiState.asStateFlow()
    private val pending = linkedSetOf<String>()
    private val active = mutableSetOf<String>()
    private var dataLoaded = false
    private var observationJob: Job? = null

    init {
        observeData()
    }

    private fun observeData() {
        if (observationJob?.isActive == true) return
        observationJob = viewModelScope.launch {
            try {
                combine(repository.observeNews(), preferencesRepository.sourceOrder) { articles, order ->
                    articles.groupBy { it.sourceId } to order
                }.collect { (articles, order) ->
                    _uiState.update { state ->
                        state.copy(
                            feeds = orderFeeds(state.feeds, order).map { feed ->
                                feed.copy(
                                    articles = articles[feed.source.id].orEmpty(),
                                    error = if (!dataLoaded && feed.error?.reason == NewsFailureReason.STORAGE) null else feed.error
                                )
                            },
                            isSourceOrderLoaded = true
                        )
                    }
                    // 首次恢复本地内容后才刷新；后续数据和排序变化不触发联网
                    if (!dataLoaded) {
                        dataLoaded = true
                        _uiState.value.feeds.forEach { enqueue(it.source.id) }
                        drainRequests()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                dataLoaded = false
                _uiState.update { state ->
                    state.copy(
                        feeds = state.feeds.map {
                            it.copy(isLoading = false, error = NewsRefreshResult.Failure(NewsFailureReason.STORAGE))
                        },
                        isSourceOrderLoaded = false
                    )
                }
            }
        }
    }

    fun selectSource(sourceId: String?) {
        if (sourceId != null && _uiState.value.feeds.none { it.source.id == sourceId }) return
        if (_uiState.value.selectedSourceId == sourceId) return
        _uiState.update { it.copy(selectedSourceId = sourceId) }
        drainRequests()
    }

    /** 写入完成后由偏好流发布顺序；失败时保留原顺序供弹窗重试 */
    suspend fun saveSourceOrder(sourceIds: List<String>): Boolean {
        if (!_uiState.value.isSourceOrderLoaded) return false
        val orderedIds = orderFeeds(_uiState.value.feeds, sourceIds).map { it.source.id }
        return try {
            preferencesRepository.saveSourceOrder(orderedIds)
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun orderFeeds(feeds: List<SourceFeedUiState>, sourceIds: List<String>): List<SourceFeedUiState> {
        val byId = feeds.associateBy { it.source.id }
        // 忽略失效、重复 ID；遗漏的来源始终按内置默认顺序追加
        return (sourceIds + defaultSourceIds).distinct().mapNotNull(byId::get)
    }

    fun refreshAll() {
        _uiState.value.feeds.forEach { enqueue(it.source.id) }
        if (!dataLoaded) observeData()
        drainRequests()
    }

    fun refresh(sourceId: String) {
        if (_uiState.value.feeds.none { it.source.id == sourceId }) return
        enqueue(sourceId)
        if (!dataLoaded) observeData()
        drainRequests()
    }

    private fun enqueue(sourceId: String) {
        if (sourceId in active || !pending.add(sourceId)) return
        updateSource(sourceId) { it.copy(isLoading = true, error = null) }
    }

    private fun drainRequests() {
        if (!dataLoaded) return
        while (active.size < 4 && pending.isNotEmpty()) {
            val sourceId = _uiState.value.selectedSourceId?.takeIf { it in pending } ?: pending.first()
            pending.remove(sourceId)
            active.add(sourceId)
            viewModelScope.launch {
                try {
                    val result = repository.refresh(sourceId)
                    // 刷新结果不携带新闻；文章列表只由数据库观察流更新
                    updateSource(sourceId) {
                        it.copy(isLoading = false, error = result as? NewsRefreshResult.Failure)
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
        fun factory(
            repository: NewsRepository,
            preferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == HomeViewModel::class.java)
                return modelClass.cast(HomeViewModel(repository, preferencesRepository))!!
            }
        }
    }
}
