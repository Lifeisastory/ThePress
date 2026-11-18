package com.aster.thepress

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import com.aster.core.data.repository.NewsRepository
import com.aster.core.design_system.theme.AppTheme
import com.aster.core.localdb.dto.NewsCacheDao
import com.aster.core.localdb.entity.CachedNewsArticleEntity
import com.aster.core.localdb.entity.NewsCacheEntity
import com.aster.core.network.NetworkClient
import com.aster.core.network.sources.BuiltInSources
import com.aster.feature.search.api.navigation.SearchNavKey
import com.aster.feature.main.api.navigation.MainNavKey
import com.aster.thepress.ui.App
import com.aster.thepress.ui.AppState
import com.aster.thepress.ui.rememberAppState
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val cache = CachedArticles()
    private val repository = NewsRepository(
        client = NetworkClient(),
        cacheDao = cache,
        definitions = BuiltInSources.all.take(2),
        currentTimeMillis = { 1_000L }
    )
    private lateinit var appState: AppState
    private lateinit var backDispatcher: OnBackPressedDispatcher

    private fun render(): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            appState = rememberAppState()
            backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            AppTheme { App(repository = repository, appState = appState) }
        }
        compose.waitUntil { cache.reads >= 4 }
        compose.waitForIdle()
        return restoration
    }

    private fun selectTab(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performClick().assertIsSelected()
    }

    private fun onBaiduPage(tag: String) = compose.onNode(
        hasTestTag(tag) and hasAnyAncestor(hasTestTag("home_source_page_baidu"))
    )

    @Test
    fun tabSwitchesStayInsideMainAndBackReturnsDirectlyToHome() {
        render()
        selectTab("收藏")
        selectTab("我的")
        compose.runOnIdle {
            assertEquals(listOf(MainNavKey), appState.navigationState.backStack.toList())
            backDispatcher.onBackPressed()
        }
        compose.onNodeWithTag("home_page").assertIsDisplayed()
        compose.onNode(hasText("首页") and hasClickAction()).assertIsSelected()
    }

    @Test
    fun homeSourceAndScrollPositionSurviveTabSwitchAndSearch() {
        render()
        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_baidu"))
        compose.onNodeWithTag("glass_tab_baidu").performClick()
        onBaiduPage("home_feed").performScrollToIndex(16)
        onBaiduPage("home_article_baidu_15").assertIsDisplayed()
        val initialReads = cache.reads

        selectTab("收藏")
        selectTab("首页")
        compose.onNodeWithTag("glass_tab_baidu").assertIsSelected()
        onBaiduPage("home_article_baidu_15").assertIsDisplayed()

        compose.onNodeWithTag("home_search_button").performClick()
        compose.onNodeWithTag("search_screen").assertIsDisplayed()
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
        compose.onNodeWithTag("liquid_glass_navigation_tabs").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(MainNavKey, SearchNavKey), appState.navigationState.backStack.toList())
        }
        compose.onNodeWithTag("search_back").performClick()
        compose.onNodeWithTag("glass_tab_baidu").assertIsSelected()
        onBaiduPage("home_article_baidu_15").assertIsDisplayed()
        compose.runOnIdle { assertEquals(initialReads, cache.reads) }
    }

    @Test
    fun restoringSearchRouteRetainsMainTabAndItsBackBehavior() {
        val restoration = render()
        selectTab("我的")
        compose.runOnIdle { appState.navigator.navigate(SearchNavKey) }
        compose.onNodeWithTag("search_screen").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("search_screen").assertIsDisplayed()
        compose.onNodeWithTag("liquid_glass_navigation_tabs").assertDoesNotExist()
        compose.onNodeWithTag("search_back").performClick()
        compose.onNodeWithTag("profile_page").assertIsDisplayed()
        compose.onNode(hasText("我的") and hasClickAction()).assertIsSelected()
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithTag("home_page").assertIsDisplayed()
    }

    /** 全部来源均有有效缓存，路由测试不依赖网络；记录读取次数以发现 ViewModel 被重建。 */
    private class CachedArticles : NewsCacheDao() {
        var reads = 0

        override suspend fun metadata(sourceId: String): NewsCacheEntity {
            reads++
            return NewsCacheEntity(sourceId, 1_000L)
        }

        override suspend fun articles(sourceId: String) = (0..30).map { index ->
            CachedNewsArticleEntity(
                sourceId = sourceId,
                position = index,
                id = index.toString(),
                title = "测试文章 $index",
                url = "https://example.com/$sourceId/$index",
                summary = null
            )
        }

        override fun search(sourceId: String?, pattern: String) = flowOf(emptyList<CachedNewsArticleEntity>())
        override suspend fun upsertMetadata(metadata: NewsCacheEntity) = error("只读缓存")
        override suspend fun deleteArticles(sourceId: String) = error("只读缓存")
        override suspend fun insertArticles(articles: List<CachedNewsArticleEntity>) = error("只读缓存")
    }
}
