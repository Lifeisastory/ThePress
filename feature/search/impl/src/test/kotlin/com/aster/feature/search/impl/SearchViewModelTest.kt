package com.aster.feature.search.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val sources = listOf(NewsSource("qqvideo-tv-hotsearch", "腾讯视频"), NewsSource("baidu", "百度"))

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(
        search: (String) -> Flow<List<NewsArticle>>
    ): SearchViewModel {
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                modelClass.cast(SearchViewModel(sources, search))!!
        }
        return ViewModelProvider(store, factory)[SearchViewModel::class.java]
    }

    private fun article(id: String) = NewsArticle("baidu", id, "标题 $id", "https://example.com/$id")

    @Test
    fun queryDebouncesCancelsOldResultsAndKeepsResultsFromEverySource() = runTest(dispatcher) {
        val searches = mutableListOf<String>()
        val oldResults = MutableStateFlow(listOf(article("old")))
        val newResults = MutableStateFlow(listOf(article("new")))
        val vm = viewModel { query ->
            searches.add(query)
            if (query == "旧") oldResults else newResults
        }
        runCurrent()
        assertTrue(searches.isEmpty())
        vm.setQuery("旧")
        advanceTimeBy(299)
        runCurrent()
        assertTrue(searches.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals("old", vm.uiState.value.results.single().id)
        vm.setQuery("新")
        assertTrue(vm.uiState.value.isSearching)
        oldResults.value = listOf(article("stale"))
        runCurrent()
        assertTrue(vm.uiState.value.results.isEmpty())
        vm.setQuery("新闻")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf("旧", "新闻"), searches)
        newResults.value = listOf(article("updated"), article("video").copy(sourceId = "qqvideo-tv-hotsearch"))
        runCurrent()
        assertEquals(listOf("baidu", "qqvideo-tv-hotsearch"), vm.uiState.value.results.map { it.sourceId })
        assertEquals(listOf("updated", "video"), vm.uiState.value.results.map { it.id })
        vm.setQuery("  ")
        newResults.value = listOf(article("after-clear"))
        runCurrent()
        assertTrue(vm.uiState.value.results.isEmpty())
        assertFalse(vm.uiState.value.isSearching)
    }

    @Test
    fun submitAndRetryDoNotWaitForDebounce() = runTest(dispatcher) {
        val searches = mutableListOf<String>()
        val vm = viewModel { query ->
            searches.add(query)
            if (searches.size == 1) flow { throw IllegalStateException("Database unavailable") }
            else flowOf(listOf(article("recovered")))
        }
        vm.setQuery(" 新闻 ")
        vm.submitSearch()
        runCurrent()
        assertTrue(vm.uiState.value.hasError)
        assertEquals(listOf("新闻"), searches)
        vm.submitSearch()
        runCurrent()
        assertFalse(vm.uiState.value.hasError)
        assertEquals("recovered", vm.uiState.value.results.single().id)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(2, searches.size)
    }

    @Test
    fun leavingSearchCancelsItsQuerySubscription() = runTest(dispatcher) {
        var cancelled = false
        val vm = viewModel { _ ->
            flow {
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        vm.setQuery("新闻")
        vm.submitSearch()
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue(cancelled)
    }
}
