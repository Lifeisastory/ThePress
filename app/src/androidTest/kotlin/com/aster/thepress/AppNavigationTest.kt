package com.aster.thepress

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
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
import com.aster.core.data.repository.DigestSettingsRepository
import com.aster.core.data.repository.DigestAgentRepository
import com.aster.core.model.AgentEvent
import com.aster.core.model.AgentMessage
import com.aster.core.model.AgentStep
import com.aster.core.model.AgentStepKind
import com.aster.core.model.AgentConversation
import com.aster.core.model.AgentConversationSummary
import com.aster.core.model.AgentRole
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import kotlinx.coroutines.CompletableDeferred
import com.aster.core.model.DigestSettings
import com.aster.core.model.DigestReference
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
    private var agentCalls = 0
    private var agentMessages = emptyList<AgentMessage>()
    private val agentSessions = mutableMapOf<Long, List<AgentMessage>>()
    private val agentSummaries = MutableStateFlow<List<AgentConversationSummary>>(emptyList())
    private var agentGate: CompletableDeferred<Unit>? = null
    private val agent = object : DigestAgentRepository {
        override fun observeConversations() = agentSummaries
        override suspend fun loadConversation(id: Long?): AgentConversation? {
            val key = id ?: agentSessions.keys.maxOrNull() ?: return null
            return agentSessions[key]?.let { AgentConversation(key, it) }
        }
        override suspend fun saveConversation(id: Long?, messages: List<AgentMessage>): Long {
            agentMessages = messages
            val key = id ?: ((agentSessions.keys.maxOrNull() ?: 0L) + 1)
            agentSessions[key] = messages
            agentSummaries.value = agentSessions.entries.sortedByDescending { it.key }.map {
                AgentConversationSummary(it.key, it.value.first().text, System.currentTimeMillis(), it.value.size)
            }
            return key
        }
        override fun reply(prompt: String, history: List<AgentMessage>) = flow {
            agentCalls++
            emit(AgentEvent.StepStarted(AgentStep("skill", "use_skill", "加载技能", "news-briefing", AgentStepKind.SKILL)))
            emit(AgentEvent.StepFinished("skill", "根据来源整理简报", true))
            emit(AgentEvent.StepStarted(AgentStep("search", "search_news", "查询资讯", "科技")))
            emit(AgentEvent.StepFinished("search", "找到一条报道", true))
            emit(AgentEvent.References(listOf(DigestReference("n1", "baidu", "百度", "1", "原始报道", "摘要", "https://example.com/agent"))))
            emit(AgentEvent.TextDelta("今日重点："))
            agentGate?.await()
            emit(AgentEvent.TextDelta("\n\n这是一份**基于所选来源**整理的测试简报\n\n- 核对事实\n- 保留来源"))
            emit(AgentEvent.Completed)
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
    private lateinit var hostActivity: ComponentActivity

    private fun render(): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            appState = rememberAppState()
            backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            hostActivity = LocalOnBackPressedDispatcherOwner.current as ComponentActivity
            DisposableEffect(hostActivity) {
                hostActivity.enableEdgeToEdge()
                hostActivity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                onDispose { }
            }
            AppTheme { App(repository = repository, preferencesRepository = preferences, appState = appState,
                digestSettingsRepository = digestSettings, digestAgentRepository = agent) }
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
    fun agentSettingsDialogIsIndependentOfProfile() {
        render()
        selectTab(DigestR.string.feature_digest_title)
        compose.onNodeWithTag("agent_send").assertIsNotEnabled()
        compose.onNodeWithTag("digest_open_settings").performClick()
        captureScreenshot("agent-settings")
        scrollToAgentSetting("digest_api_key")
        compose.onNodeWithTag("digest_api_key").performTextInput("test-key")
        compose.onNodeWithTag("digest_api_key").performImeAction()
        scrollToAgentSetting("digest_source_picker")
        compose.onNodeWithTag("digest_source_picker").performClick()
        scrollToAgentSetting("digest_source_baidu")
        compose.onNodeWithTag("digest_source_baidu").performClick()
        scrollToAgentSetting("digest_save_settings")
        compose.onNodeWithTag("digest_save_settings").performClick()
        compose.waitUntil { digestSettings.settings.value.hasApiKey }
        compose.runOnIdle { assertEquals(setOf("baidu"), digestSettings.settings.value.sourceIds) }
        compose.onNodeWithTag("digest_close_settings").performClick()
        selectTab(MeR.string.feature_me_title)
        compose.onNodeWithTag("digest_api_key").assertDoesNotExist()
        selectTab(DigestR.string.feature_digest_title)
        compose.onNodeWithTag("digest_open_settings").performClick()
        compose.onNodeWithTag("digest_settings_scroll").assertIsDisplayed()
        compose.onNodeWithTag("digest_close_settings").performClick()
    }

    @Test
    fun assistantStreamsShowsSkillsAndToolsAndOpensSources() {
        digestSettings.settings.value = DigestSettings(setOf("baidu"), hasApiKey = true)
        agentGate = CompletableDeferred()
        render()
        selectTab(DigestR.string.feature_digest_title)
        compose.runOnIdle { assertEquals(0, agentCalls) }
        compose.onNodeWithTag("agent_input").assertDoesNotExist()
        compose.onNodeWithTag("agent_send").performClick()
        compose.onNodeWithTag("agent_input").performTextInput("整理科技简报")
        compose.waitUntil(5000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(hostActivity.window.decorView)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom?.let { it > 0 } == true
        }
        captureScreenshot("agent-input")
        var imeHeight = 0
        var windowHeight = 0
        compose.runOnIdle {
            imeHeight = androidx.core.view.ViewCompat.getRootWindowInsets(hostActivity.window.decorView)!!
                .getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            windowHeight = hostActivity.window.decorView.height
        }
        val inputBottom = compose.onNodeWithTag("agent_input").fetchSemanticsNode().boundsInRoot.bottom
        compose.onNodeWithTag("digest_history").assertIsDisplayed()
        org.junit.Assert.assertTrue("输入区应贴近键盘上缘", windowHeight - imeHeight - inputBottom < 90)
        compose.onNodeWithTag("agent_input").performImeAction()
        compose.waitUntil { agentCalls == 1 }
        compose.onNodeWithTag("agent_input").assertDoesNotExist()
        compose.onNodeWithTag("digest_page").performScrollToNode(hasText("今日重点："))
        compose.onNodeWithText("今日重点：").assertIsDisplayed()
        compose.onNodeWithTag("agent_stop").assertIsDisplayed()
        compose.runOnIdle { agentGate!!.complete(Unit) }
        compose.waitUntil { agentMessages.lastOrNull()?.text?.contains("测试简报") == true }
        compose.onNodeWithTag("agent_step_skill").assertDoesNotExist()
        compose.onNodeWithTag("digest_page").performScrollToNode(hasText("工具与技能 · 2 项"))
        compose.onNodeWithText("工具与技能 · 2 项").performClick()
        compose.onNodeWithText("加载技能").assertIsDisplayed()
        compose.onNodeWithText("查询资讯").assertIsDisplayed()
        compose.onNodeWithTag("agent_step_detail_skill").assertDoesNotExist()
        compose.onNodeWithTag("agent_step_skill").performClick()
        compose.onNodeWithTag("agent_step_detail_skill").assertIsDisplayed()
        compose.onNodeWithTag("agent_step_skill").performClick()
        compose.onNode(hasText("**基于所选来源**", substring = true)).assertDoesNotExist()
        val userBounds = compose.onNodeWithTag("agent_message_${agentMessages.first().id}").fetchSemanticsNode().boundsInRoot
        val assistantBounds = compose.onNodeWithTag("agent_message_${agentMessages.last().id}").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(userBounds.left > assistantBounds.left)
        org.junit.Assert.assertTrue(userBounds.width < assistantBounds.width)
        captureScreenshot("agent-ui")
        compose.onNodeWithTag("digest_page").performScrollToNode(hasText("查看本次查阅的 1 条来源"))
        compose.onNodeWithText("查看本次查阅的 1 条来源").performClick()
        compose.onNodeWithTag("digest_page").performScrollToNode(hasTestTag("agent_source_n1"))
        compose.onNodeWithTag("agent_source_n1").performClick()
        compose.onNodeWithTag("webview_screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(WebViewNavKey("https://example.com/agent"), appState.navigationState.currentKey) }
        compose.onNodeWithTag("webview_close").performClick()
        compose.onNodeWithTag("agent_new").performClick()
        compose.onNodeWithTag("agent_input").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, agentSessions.size) }
        compose.onNodeWithTag("digest_side_by_side").performTouchInput { swipeRight() }
        compose.onNodeWithTag("agent_conversation_1").assertIsDisplayed()
        captureScreenshot("agent-history")
        compose.onNodeWithTag("agent_conversation_1").performClick()
        compose.onNodeWithTag("digest_page").performScrollToNode(hasText("整理科技简报"))
        compose.onNodeWithText("整理科技简报").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, agentCalls) }
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

    private fun scrollToAgentSetting(tag: String) {
        val settings = compose.onNodeWithTag("digest_settings_scroll")
        settings.performScrollToNode(hasTestTag(tag))
    }

    private fun captureScreenshot(name: String) {
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        checkNotNull(instrumentation.uiAutomation.takeScreenshot()).let { bitmap ->
            val screenshot = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
            screenshot.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            instrumentation.uiAutomation.executeShellCommand("cp ${screenshot.absolutePath} /data/local/tmp/thepress-$name.png")
                .use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes() }
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
