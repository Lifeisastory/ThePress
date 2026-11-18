package com.aster.feature.home.impl

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsFailureReason
import com.aster.core.model.NewsRefreshResult
import com.aster.core.model.NewsSource
import com.aster.core.data.repository.NewsRepository
import com.aster.core.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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

    private val articles = MutableStateFlow<List<NewsArticle>>(emptyList())
    private val preferences = FakePreferences()

    private class FakePreferences : UserPreferencesRepository {
        override val sourceOrder = MutableStateFlow<List<String>>(emptyList())
        var failure: IOException? = null
        override suspend fun saveSourceOrder(sourceIds: List<String>) {
            failure?.let { throw it }
            sourceOrder.value = sourceIds
        }
    }

    private fun viewModel(
        sources: List<NewsSource> = this.sources,
        observe: () -> Flow<List<NewsArticle>> = { articles },
        load: suspend (String) -> NewsRefreshResult
    ): HomeViewModel {
        val repository = object : NewsRepository {
            override val sources = sources
            override fun observeNews() = observe()
            override fun search(query: String, sourceId: String?) = articles
            override suspend fun refresh(sourceId: String) = load(sourceId)
        }
        return ViewModelProvider(store, HomeViewModel.factory(repository, preferences))[HomeViewModel::class.java]
    }

    private fun article(sourceId: String, id: String = "1") =
        NewsArticle(sourceId, id, "标题 $id", "https://example.com/$id")

    @Test
    fun expiredSnapshotIsVisibleWhenStartupRefreshFails() = runTest(dispatcher) {
        val cached = article("baidu", "cached")
        articles.value = listOf(cached)
        val vm = viewModel {
            NewsRefreshResult.Failure(NewsFailureReason.CONNECTION)
        }
        runCurrent()
        assertEquals(listOf(cached), vm.uiState.value.feeds.last().articles)
        assertEquals(NewsFailureReason.CONNECTION, vm.uiState.value.feeds.last().error?.reason)
    }

    @Test
    fun limitsConcurrencyAndPrioritizesSelectedQueuedSource() = runTest(dispatcher) {
        val many = (1..8).map { NewsSource(it.toString(), "来源 $it") }
        val responses = many.associate { it.id to CompletableDeferred<NewsRefreshResult>() }
        val calls = mutableListOf<String>()
        val vm = viewModel(sources = many) {
            calls.add(it)
            responses.getValue(it).await()
        }
        runCurrent()
        assertEquals(listOf("1", "2", "3", "4"), calls)
        vm.selectSource("8")
        responses.getValue("1").complete(NewsRefreshResult.Success)
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
        val responses = sources.associate { it.id to CompletableDeferred<NewsRefreshResult>() }
        val viewModel = viewModel { responses.getValue(it).await() }
        runCurrent()
        assertTrue(viewModel.uiState.value.feeds.all { it.isLoading })

        responses.getValue(sources.first().id).complete(NewsRefreshResult.Failure(NewsFailureReason.CONNECTION))
        runCurrent()
        assertFalse(viewModel.uiState.value.feeds.first().isLoading)
        assertTrue(viewModel.uiState.value.feeds.last().isLoading)

        val news = article("baidu")
        articles.value = listOf(news)
        responses.getValue("baidu").complete(NewsRefreshResult.Success)
        runCurrent()
        assertEquals(NewsFailureReason.CONNECTION, viewModel.uiState.value.feeds.first().error?.reason)
        assertEquals(listOf(news), viewModel.uiState.value.feeds.last().articles)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun repeatedRefreshDoesNotDuplicateActiveRequestsAndRetryTargetsOneSource() = runTest(dispatcher) {
        val responses = sources.associate { it.id to CompletableDeferred<NewsRefreshResult>() }
        val calls = mutableListOf<String>()
        val viewModel = viewModel {
            calls.add(it)
            responses.getValue(it).await()
        }
        repeat(3) { viewModel.refreshAll() }
        runCurrent()
        assertEquals(sources.map { it.id }, calls)

        responses.values.forEach { it.complete(NewsRefreshResult.Success) }
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
        articles.value = listOf(old)
        val responses = ArrayDeque(listOf(
            NewsRefreshResult.Success,
            NewsRefreshResult.Failure(NewsFailureReason.TIMEOUT),
            NewsRefreshResult.Success
        ))
        val viewModel = viewModel {
            if (it == "baidu") responses.removeFirst() else NewsRefreshResult.Success
        }
        runCurrent()
        viewModel.refresh("baidu")
        runCurrent()
        assertEquals(listOf(old), viewModel.uiState.value.feeds.last().articles)
        assertEquals(NewsFailureReason.TIMEOUT, viewModel.uiState.value.feeds.last().error?.reason)

        viewModel.refresh("baidu")
        runCurrent()
        // 刷新成功不会直接替换文章，必须等数据库流发出已落库的内容。
        assertEquals(listOf(old), viewModel.uiState.value.feeds.last().articles)
        articles.value = listOf(fresh)
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

    @Test
    fun databaseAndPreferenceUpdatesDoNotTriggerExtraRefreshes() = runTest(dispatcher) {
        val calls = mutableListOf<String>()
        val vm = viewModel { calls.add(it); NewsRefreshResult.Success }
        runCurrent()
        vm.selectSource("baidu")
        articles.value = listOf(article("baidu", "external"))
        preferences.sourceOrder.value = listOf("baidu", "removed", "baidu")
        runCurrent()
        assertEquals(listOf("baidu", sources.first().id), vm.uiState.value.feeds.map { it.source.id })
        assertEquals("external", vm.uiState.value.feeds.first().articles.single().id)
        assertEquals("baidu", vm.uiState.value.selectedSourceId)
        assertEquals(sources.map { it.id }, calls)

        preferences.sourceOrder.value = emptyList()
        articles.value = emptyList()
        runCurrent()
        assertEquals(sources.map { it.id }, vm.uiState.value.feeds.map { it.source.id })
        assertTrue(vm.uiState.value.feeds.all { it.articles.isEmpty() })
        assertEquals(2, calls.size)
    }

    @Test
    fun sourceOrderSaveFailureKeepsOrderAndSuccessfulRetryIsObserved() = runTest(dispatcher) {
        val vm = viewModel { NewsRefreshResult.Success }
        assertFalse(vm.saveSourceOrder(listOf("baidu")))
        runCurrent()
        preferences.failure = IOException("disk full")
        assertFalse(vm.saveSourceOrder(listOf("baidu")))
        runCurrent()
        assertEquals(sources.map { it.id }, vm.uiState.value.feeds.map { it.source.id })

        preferences.failure = null
        assertTrue(vm.saveSourceOrder(listOf("baidu", "removed", "baidu")))
        runCurrent()
        assertEquals(listOf("baidu", sources.first().id), vm.uiState.value.feeds.map { it.source.id })
        assertEquals(listOf("baidu", sources.first().id), preferences.sourceOrder.value)
    }

    @Test
    fun databaseReadFailureCanBeRetriedWithoutRecreatingViewModel() = runTest(dispatcher) {
        var fail = true
        val vm = viewModel(observe = {
            if (fail) flow { throw IOException("unavailable") } else articles
        }) { NewsRefreshResult.Success }
        runCurrent()
        assertTrue(vm.uiState.value.feeds.all { it.error?.reason == NewsFailureReason.STORAGE })
        fail = false
        articles.value = listOf(article("baidu", "recovered"))
        vm.refreshAll()
        runCurrent()
        assertEquals("recovered", vm.uiState.value.feeds.last().articles.single().id)
        assertTrue(vm.uiState.value.feeds.all { it.error == null && !it.isLoading })
    }
}
