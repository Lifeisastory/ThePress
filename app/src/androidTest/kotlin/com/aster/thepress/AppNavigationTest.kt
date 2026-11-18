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
import com.aster.core.data.repository.UserPreferencesRepository
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsSource
import com.aster.core.model.NewsRefreshResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import com.aster.core.design_system.theme.AppTheme
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

    private val repository = TestNewsRepository()
    private val preferences = object : UserPreferencesRepository {
        override val sourceOrder = MutableStateFlow<List<String>>(emptyList())
        override suspend fun saveSourceOrder(sourceIds: List<String>) { sourceOrder.value = sourceIds }
    }
    private lateinit var appState: AppState
    private lateinit var backDispatcher: OnBackPressedDispatcher

    private fun render(): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            appState = rememberAppState()
            backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            AppTheme { App(repository = repository, preferencesRepository = preferences, appState = appState) }
        }
        compose.waitUntil { repository.observations >= 1 }
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
        val initialReads = repository.observations

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
        compose.runOnIdle { assertEquals(initialReads, repository.observations) }
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

    /** 使用 data 的公开接口模拟数据；导航测试不接触数据库和网络。 */
    private class TestNewsRepository : NewsRepository {
        var observations = 0
        override val sources = listOf(NewsSource("qqvideo-tv-hotsearch", "腾讯视频"), NewsSource("baidu", "百度"))
        override fun observeNews() = flow {
            observations++
            emit(sources.flatMap { source ->
                (0..30).map { index ->
                    NewsArticle(source.id, index.toString(), "测试文章 $index", "https://example.com/${source.id}/$index")
                }
            })
        }
        override fun search(query: String, sourceId: String?) = flowOf(emptyList<NewsArticle>())
        override suspend fun refresh(sourceId: String) = NewsRefreshResult.Success
    }
}
