package com.aster.thepress

import androidx.activity.OnBackPressedDispatcher
import androidx.annotation.StringRes
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
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
import com.aster.feature.home.api.R as HomeR
import com.aster.feature.me.api.R as MeR
import com.aster.feature.saved.api.R as SavedR

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

    private fun tab(@StringRes labelRes: Int) = compose.onNode(
        hasText(InstrumentationRegistry.getInstrumentation().targetContext.getString(labelRes)) and hasClickAction()
    )

    private fun selectTab(@StringRes labelRes: Int) {
        tab(labelRes).performClick().assertIsSelected()
    }

    private fun onBaiduPage(tag: String) = compose.onNode(
        hasTestTag(tag) and hasAnyAncestor(hasTestTag("home_source_page_baidu"))
    )

    @Test
    fun tabSwitchesStayInsideMainAndBackReturnsDirectlyToHome() {
        render()
        selectTab(SavedR.string.feature_saved_title)
        selectTab(MeR.string.feature_me_title)
        compose.runOnIdle {
            assertEquals(listOf(MainNavKey), appState.navigationState.backStack.toList())
            backDispatcher.onBackPressed()
        }
        compose.onNodeWithTag("home_page").assertIsDisplayed()
        tab(HomeR.string.feature_home_title).assertIsSelected()
    }

    @Test
    fun homeSourceAndScrollPositionSurviveTabSwitchAndSearch() {
        render()
        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_baidu"))
        compose.onNodeWithTag("glass_tab_baidu").performClick()
        onBaiduPage("home_feed").performScrollToIndex(15)
        onBaiduPage("home_article_baidu_15").assertIsDisplayed()
        val initialReads = cache.reads

        selectTab(SavedR.string.feature_saved_title)
        selectTab(HomeR.string.feature_home_title)
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
        selectTab(MeR.string.feature_me_title)
        compose.runOnIdle { appState.navigator.navigate(SearchNavKey) }
        compose.onNodeWithTag("search_screen").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("search_screen").assertIsDisplayed()
        compose.onNodeWithTag("liquid_glass_navigation_tabs").assertDoesNotExist()
        compose.onNodeWithTag("search_back").performClick()
        compose.onNodeWithTag("profile_page").assertIsDisplayed()
        tab(MeR.string.feature_me_title).assertIsSelected()
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithTag("home_page").assertIsDisplayed()
    }

    @Test
    fun scrollToTopAppearsAfterOneViewportAndReturnsCurrentSourceToTop() {
        render()
        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_baidu"))
        compose.onNodeWithTag("glass_tab_baidu").performClick()
        val button = compose.onNodeWithTag("liquid_glass_scroll_to_top")
        val feed = onBaiduPage("home_feed")
        val viewportHeight = feed.fetchSemanticsNode().boundsInRoot.height
        button.assertDoesNotExist()

        feed.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, viewportHeight * 0.75f) }
        button.assertDoesNotExist()
        feed.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, viewportHeight * 0.75f) }
        button.assertIsDisplayed().performClick()

        button.assertDoesNotExist()
        onBaiduPage("home_article_baidu_0").assertIsDisplayed()
        compose.onNodeWithTag("glass_tab_baidu").assertIsSelected()
    }

    @Test
    fun scrollToTopFollowsActiveSourceAndRestoredPosition() {
        val restoration = render()
        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_baidu"))
        compose.onNodeWithTag("glass_tab_baidu").performClick()
        onBaiduPage("home_feed").performScrollToIndex(15)
        val button = compose.onNodeWithTag("liquid_glass_scroll_to_top")
        button.assertIsDisplayed()

        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_all"))
        compose.onNodeWithTag("glass_tab_all").performClick()
        button.assertDoesNotExist()
        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_baidu"))
        compose.onNodeWithTag("glass_tab_baidu").performClick()
        button.assertIsDisplayed()

        selectTab(SavedR.string.feature_saved_title)
        button.assertDoesNotExist()
        selectTab(HomeR.string.feature_home_title)
        button.assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        button.assertIsDisplayed().performClick()
        button.assertDoesNotExist()
        onBaiduPage("home_article_baidu_0").assertIsDisplayed()
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
