package com.aster.feature.home.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsFailureReason
import com.aster.core.model.NewsLoadResult
import com.aster.core.model.NewsSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val sources = listOf(NewsSource("qqvideo-tv-hotsearch", "腾讯视频"), NewsSource("baidu", "百度"))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(
        cached: suspend (String) -> List<NewsArticle>? = { null },
        search: (String, String?) -> Flow<List<NewsArticle>> = { _, _ -> flowOf(emptyList()) },
        load: suspend (String) -> NewsLoadResult
    ): HomeViewModel {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                modelClass.cast(HomeViewModel(sources, load, cached, search))!!
        }
        return ViewModelProvider(store, factory)[HomeViewModel::class.java]
    }

    private fun article(sourceId: String, id: String = "1") =
        NewsArticle(sourceId, id, "标题 $id", "https://example.com/$id")

    @Test
    fun expiredSnapshotIsVisibleWhenStartupRefreshFails() = runTest(dispatcher) {
        val cached = article("baidu", "cached")
        val vm = viewModel(cached = { if (it == "baidu") listOf(cached) else null }) {
            NewsLoadResult.Failure(NewsFailureReason.CONNECTION)
        }
        runCurrent()
        assertEquals(listOf(cached), vm.uiState.value.feeds.last().articles)
        assertEquals(NewsFailureReason.CONNECTION, vm.uiState.value.feeds.last().error?.reason)
    }

    @Test
    fun searchDebouncesCancelsOldResultsAndFiltersWithoutFetching() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        val searches = mutableListOf<Pair<String, String?>>()
        val oldResults = MutableStateFlow(listOf(article("baidu", "old")))
        val newResults = MutableStateFlow(listOf(article("baidu", "new")))
        val vm = viewModel(search = { query, source ->
            searches.add(query to source)
            if (query == "旧") oldResults else newResults
        }) {
            calls.add(it)
            NewsLoadResult.Success(listOf(article(it, "browse")))
        }
        runCurrent()
        vm.setQuery("旧")
        advanceTimeBy(300)
        runCurrent()
        assertEquals("old", vm.uiState.value.visibleArticles.single().id)
        vm.setQuery("新")
        assertTrue(vm.uiState.value.isSearching)
        oldResults.value = listOf(article("baidu", "stale"))
        runCurrent()
        assertTrue(vm.uiState.value.visibleArticles.isEmpty())
        vm.setQuery("新闻")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf("旧" to null, "新闻" to null), searches)
        vm.selectSource("baidu")
        runCurrent()
        assertEquals("新闻" to "baidu", searches.last())
        newResults.value = listOf(article("baidu", "updated"))
        runCurrent()
        assertEquals("updated", vm.uiState.value.visibleArticles.single().id)
        vm.setQuery("")
        runCurrent()
        assertEquals("browse", vm.uiState.value.visibleArticles.single().id)
        assertEquals(sources.map { it.id }, calls)
    }

    @Test
    fun limitsConcurrencyAndPrioritizesSelectedQueuedSource() = runTest(dispatcher) {
        val many = (1..8).map { NewsSource(it.toString(), "来源 $it") }
        val responses = many.associate { it.id to CompletableDeferred<NewsLoadResult>() }
        val calls = mutableListOf<String>()
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(
                HomeViewModel(many, loadSource = {
                    calls.add(it)
                    responses.getValue(it).await()
                })
            )!!
        }
        val vm = ViewModelProvider(store, factory)[HomeViewModel::class.java]
        runCurrent()
        assertEquals(listOf("1", "2", "3", "4"), calls)
        vm.selectSource("8")
        responses.getValue("1").complete(NewsLoadResult.Success(emptyList()))
        runCurrent()
        assertEquals(listOf("1", "2", "3", "4", "8"), calls)
        vm.refresh("8")
        runCurrent()
        assertEquals(1, calls.count { it == "8" })
        store.clear()
        runCurrent()
        assertEquals(5, calls.size)
    }

    @Test
    fun sourcesLoadIndependentlyAndOneFailureDoesNotHideTheOther() = runTest(dispatcher) {
        val responses = sources.associate { it.id to CompletableDeferred<NewsLoadResult>() }
        val viewModel = viewModel { responses.getValue(it).await() }
        runCurrent()
        assertTrue(viewModel.uiState.value.feeds.all { it.isLoading })

        responses.getValue(sources.first().id).complete(NewsLoadResult.Failure(NewsFailureReason.CONNECTION))
        runCurrent()
        assertFalse(viewModel.uiState.value.feeds.first().isLoading)
        assertTrue(viewModel.uiState.value.feeds.last().isLoading)

        val news = article("baidu")
        responses.getValue("baidu").complete(NewsLoadResult.Success(listOf(news)))
        runCurrent()
        assertEquals(NewsFailureReason.CONNECTION, viewModel.uiState.value.feeds.first().error?.reason)
        assertEquals(listOf(news), viewModel.uiState.value.feeds.last().articles)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun repeatedRefreshDoesNotDuplicateActiveRequestsAndRetryTargetsOneSource() = runTest(dispatcher) {
        val responses = sources.associate { it.id to CompletableDeferred<NewsLoadResult>() }
        val calls = mutableListOf<String>()
        val viewModel = viewModel {
            calls.add(it)
            responses.getValue(it).await()
        }
        repeat(3) { viewModel.refreshAll() }
        runCurrent()
        assertEquals(sources.map { it.id }, calls)

        responses.values.forEach { it.complete(NewsLoadResult.Success(emptyList())) }
        runCurrent()
        viewModel.refresh("baidu")
        runCurrent()
        assertEquals(1, calls.count { it == sources.first().id })
        assertEquals(2, calls.count { it == "baidu" })
    }

    @Test
    fun refreshFailureKeepsOldContentAndSuccessfulRetryReplacesIt() = runTest(dispatcher) {
        val old = article("baidu", "old")
        val fresh = article("baidu", "fresh")
        val responses = ArrayDeque(listOf(
            NewsLoadResult.Success(listOf(old)),
            NewsLoadResult.Failure(NewsFailureReason.TIMEOUT),
            NewsLoadResult.Success(listOf(fresh))
        ))
        val viewModel = viewModel {
            if (it == "baidu") responses.removeFirst() else NewsLoadResult.Success(emptyList())
        }
        runCurrent()
        viewModel.refresh("baidu")
        runCurrent()
        assertEquals(listOf(old), viewModel.uiState.value.feeds.last().articles)
        assertEquals(NewsFailureReason.TIMEOUT, viewModel.uiState.value.feeds.last().error?.reason)

        viewModel.refresh("baidu")
        runCurrent()
        assertEquals(listOf(fresh), viewModel.uiState.value.feeds.last().articles)
        assertNull(viewModel.uiState.value.feeds.last().error)
    }

    @Test
    fun clearingTheViewModelCancelsBothRequests() = runTest(dispatcher) {
        val canceled = mutableSetOf<String>()
        viewModel { sourceId ->
            try {
                awaitCancellation()
            } finally {
                canceled.add(sourceId)
            }
        }
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals(sources.map { it.id }.toSet(), canceled)
    }
}
