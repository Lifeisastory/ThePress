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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import com.aster.core.data.repository.NewsRepository
import com.aster.core.data.repository.UserPreferencesRepository
import com.aster.core.data.repository.NewsDigestRepository
import com.aster.core.data.repository.DigestSettingsRepository
import com.aster.core.model.DigestSettings
import com.aster.core.model.NewsDigest
import com.aster.core.model.DigestGenerationResult
import com.aster.core.model.DigestDetail
import com.aster.core.model.DigestItem
import com.aster.core.model.DigestReference
import com.aster.core.model.DigestSourceStatus
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import com.aster.core.model.AppearanceSettings
import com.aster.core.model.ThemeMode
import com.aster.core.model.ThemeColor
import com.aster.core.model.NewsCacheStats
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsSource
import com.aster.core.model.NewsRefreshResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import com.aster.core.design_system.theme.AppTheme
import com.aster.feature.search.api.navigation.SearchNavKey
import com.aster.feature.main.api.navigation.MainNavKey
import com.aster.feature.webview.api.navigation.WebViewNavKey
import com.aster.thepress.ui.App
import com.aster.thepress.ui.AppState
import com.aster.thepress.ui.rememberAppState
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import com.aster.feature.for_you.api.R as ForYouR
import com.aster.feature.me.api.R as MeR
import com.aster.feature.digest.api.R as DigestR

class AppNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val repository = TestNewsRepository()
    private val digestSettings = object : DigestSettingsRepository {
        override val settings = MutableStateFlow(DigestSettings())
        override suspend fun save(settings: DigestSettings, apiKey: String?) {
            this.settings.value = settings.copy(hasApiKey = apiKey?.isNotBlank() ?: this.settings.value.hasApiKey)
        }
    }
    private var generations = 0
    private val digestSnapshot = MutableStateFlow<NewsDigest?>(null)
    private val digests = object : NewsDigestRepository {
        override fun observeLatestDigest() = digestSnapshot
        override suspend fun generateDigest(): DigestGenerationResult {
            generations++
            digestSnapshot.value = NewsDigest(123, "deepseek-flash", DigestDetail.STANDARD,
                listOf(DigestSourceStatus("baidu", "百度", 1, false)), 1,
                listOf(DigestItem("测试简报", "合并后的新闻内容", listOf(
                    DigestReference("n1", "baidu", "百度", "1", "原始报道", "摘要", "https://example.com/digest")
                ))))
            return DigestGenerationResult.Success
        }
    }
    private val preferences = object : UserPreferencesRepository {
        override val appearance = MutableStateFlow(AppearanceSettings())
        override suspend fun saveThemeMode(mode: ThemeMode) { appearance.value = appearance.value.copy(mode = mode) }
        override suspend fun saveThemeColor(color: ThemeColor) { appearance.value = appearance.value.copy(color = color) }
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
            AppTheme { App(repository = repository, preferencesRepository = preferences, appState = appState,
                digestRepository = digests, digestSettingsRepository = digestSettings) }
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
        hasTestTag(tag) and hasAnyAncestor(hasTestTag("for_you_source_page_baidu"))
    )

    @Test
    fun digestSettingsGenerationAndCitationUseExistingWebViewNavigation() {
        render()
        selectTab(DigestR.string.feature_digest_title)
        compose.onNodeWithTag("digest_generate").assertIsNotEnabled()
        compose.onNodeWithTag("digest_open_settings").performClick()
        scrollToProfileSetting("digest_api_key")
        compose.onNodeWithTag("digest_api_key").performTextInput("test-key")
        compose.onNodeWithTag("digest_api_key").performImeAction()
        scrollToProfileSetting("digest_source_picker")
        compose.onNodeWithTag("digest_source_picker").performClick()
        scrollToProfileSetting("digest_source_baidu")
        compose.onNodeWithTag("digest_source_baidu").performClick()
        scrollToProfileSetting("digest_save_settings")
        compose.onNodeWithTag("digest_save_settings").performClick()
        compose.waitUntil { digestSettings.settings.value.hasApiKey }
        compose.runOnIdle {
            assertEquals(setOf("baidu"), digestSettings.settings.value.sourceIds)
            assertEquals(0, generations)
        }
        selectTab(DigestR.string.feature_digest_title)
        compose.onNodeWithTag("digest_generate").performClick()
        compose.onNodeWithTag("digest_page").performScrollToNode(hasTestTag("digest_link_n1"))
        compose.onNodeWithTag("digest_link_n1").performClick()
        compose.onNodeWithTag("webview_screen").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(MainNavKey, WebViewNavKey("https://example.com/digest")), appState.navigationState.backStack.toList())
            assertEquals(1, generations)
        }
        compose.onNodeWithTag("webview_close").performClick()
        compose.onNodeWithText("测试简报").assertIsDisplayed()
    }

    @Test
    fun tabSwitchesStayInsideMainAndBackReturnsDirectlyToForYou() {
        render()
        selectTab(DigestR.string.feature_digest_title)
        selectTab(MeR.string.feature_me_title)
        compose.runOnIdle {
            assertEquals(listOf(MainNavKey), appState.navigationState.backStack.toList())
            backDispatcher.onBackPressed()
        }
        compose.onNodeWithTag("for_you_page").assertIsDisplayed()
        tab(ForYouR.string.feature_for_you_title).assertIsSelected()
    }

    // 列表视口延伸到悬浮导航栏后方，点击前将控件滚动到可交互区域
    private fun scrollToProfileSetting(tag: String) {
        val settings = compose.onNodeWithTag("profile_settings")
        settings.performScrollToNode(hasTestTag(tag))
        val bottom = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.bottom
        val navigationTop = compose.onNodeWithTag("liquid_glass_navigation_tabs").fetchSemanticsNode().boundsInRoot.top
        if (bottom > navigationTop - 32f) {
            settings.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, bottom - navigationTop + 32f) }
        }
    }

    @Test
    fun forYouSourceAndScrollPositionSurviveTabSwitchAndSearch() {
        render()
        compose.onNodeWithTag("for_you_source_tabs").performScrollToNode(hasTestTag("glass_tab_baidu"))
        compose.onNodeWithTag("glass_tab_baidu").performClick()
        onBaiduPage("for_you_feed").performScrollToIndex(15)
        onBaiduPage("for_you_article_baidu_15").assertIsDisplayed()
        val initialReads = repository.observations

        selectTab(DigestR.string.feature_digest_title)
        selectTab(ForYouR.string.feature_for_you_title)
        compose.onNodeWithTag("glass_tab_baidu").assertIsSelected()
        onBaiduPage("for_you_article_baidu_15").assertIsDisplayed()

        compose.onNodeWithTag("for_you_search_button").performClick()
        compose.onNodeWithTag("search_screen").assertIsDisplayed()
        compose.onNodeWithTag("main_screen").assertDoesNotExist()
        compose.onNodeWithTag("liquid_glass_navigation_tabs").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(MainNavKey, SearchNavKey), appState.navigationState.backStack.toList())
        }
        compose.onNodeWithTag("search_back").performClick()
        compose.onNodeWithTag("glass_tab_baidu").assertIsSelected()
        onBaiduPage("for_you_article_baidu_15").assertIsDisplayed()
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
        compose.onNodeWithTag("for_you_page").assertIsDisplayed()
    }

    @Test
    fun articleOpensInWebViewAndClosingRestoresForYouPosition() {
        val restoration = render()
        compose.onNodeWithTag("for_you_source_tabs").performScrollToNode(hasTestTag("glass_tab_baidu"))
        compose.onNodeWithTag("glass_tab_baidu").performClick()
        onBaiduPage("for_you_feed").performScrollToIndex(15)
        onBaiduPage("for_you_article_baidu_15").performClick()

        compose.onNodeWithTag("webview_screen").assertIsDisplayed()
        compose.onNodeWithTag("liquid_glass_navigation_tabs").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(
                listOf(MainNavKey, WebViewNavKey("https://example.com/baidu/15")),
                appState.navigationState.backStack.toList()
            )
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("webview_screen").assertIsDisplayed()
        compose.onNodeWithTag("webview_close").performClick()
        compose.onNodeWithTag("glass_tab_baidu").assertIsSelected()
        onBaiduPage("for_you_article_baidu_15").assertIsDisplayed()
    }

    @Test
    fun searchArticleOpensInWebViewAndBackRestoresResults() {
        render()
        compose.onNodeWithTag("for_you_search_button").performClick()
        compose.onNodeWithTag("search_input").performTextInput("测试")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("search_article_baidu_search").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("search_article_baidu_search").performClick()
        compose.onNodeWithTag("webview_screen").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                listOf(MainNavKey, SearchNavKey, WebViewNavKey("https://example.com/search")),
                appState.navigationState.backStack.toList()
            )
            backDispatcher.onBackPressed()
        }
        compose.onNodeWithTag("search_screen").assertIsDisplayed()
        compose.onNodeWithTag("search_article_baidu_search").assertIsDisplayed()
    }

    @Test
    fun scrollToTopAppearsAfterOneViewportAndReturnsCurrentSourceToTop() {
        render()
        compose.onNodeWithTag("for_you_source_tabs").performScrollToNode(hasTestTag("glass_tab_baidu"))
        compose.onNodeWithTag("glass_tab_baidu").performClick()
        val button = compose.onNodeWithTag("liquid_glass_scroll_to_top")
        val feed = onBaiduPage("for_you_feed")
        val viewportHeight = feed.fetchSemanticsNode().boundsInRoot.height
        button.assertDoesNotExist()

        feed.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, viewportHeight * 0.75f) }
        button.assertDoesNotExist()
        feed.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, viewportHeight * 0.75f) }
        button.assertIsDisplayed().performClick()

        button.assertDoesNotExist()
        onBaiduPage("for_you_article_baidu_0").assertIsDisplayed()
        compose.onNodeWithTag("glass_tab_baidu").assertIsSelected()
    }

    @Test
    fun scrollToTopFollowsActiveSourceAndRestoredPosition() {
        val restoration = render()
        compose.onNodeWithTag("for_you_source_tabs").performScrollToNode(hasTestTag("glass_tab_baidu"))
        compose.onNodeWithTag("glass_tab_baidu").performClick()
        onBaiduPage("for_you_feed").performScrollToIndex(15)
        val button = compose.onNodeWithTag("liquid_glass_scroll_to_top")
        button.assertIsDisplayed()

        compose.onNodeWithTag("for_you_source_tabs").performScrollToNode(hasTestTag("glass_tab_all"))
        compose.onNodeWithTag("glass_tab_all").performClick()
        button.assertDoesNotExist()
        compose.onNodeWithTag("for_you_source_tabs").performScrollToNode(hasTestTag("glass_tab_baidu"))
        compose.onNodeWithTag("glass_tab_baidu").performClick()
        button.assertIsDisplayed()

        selectTab(DigestR.string.feature_digest_title)
        button.assertDoesNotExist()
        selectTab(ForYouR.string.feature_for_you_title)
        button.assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        button.assertIsDisplayed().performClick()
        button.assertDoesNotExist()
        onBaiduPage("for_you_article_baidu_0").assertIsDisplayed()
    }

    /** 使用 data 的公开接口模拟列表，不依赖真实资讯接口和数据库；网页内容不参与断言 */
    private class TestNewsRepository : NewsRepository {
        override fun observeCacheStats() = flowOf(NewsCacheStats(0, 0))
        override suspend fun clearCache() = Unit
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
        override fun search(query: String, sourceId: String?) = flowOf(
            listOf(NewsArticle("baidu", "search", "测试搜索文章", "https://example.com/search"))
        )
        override suspend fun refresh(sourceId: String) = NewsRefreshResult.Success
    }
}
