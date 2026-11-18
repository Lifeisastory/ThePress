package com.aster.feature.home.impl

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsSource
import com.aster.feature.home.impl.model.HomeUiState
import com.aster.feature.home.impl.model.SourceFeedUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = mutableStateOf(HomeUiState(
        feeds = listOf("one" to "来源一", "two" to "来源二").map { (id, name) ->
            SourceFeedUiState(
                NewsSource(id, name),
                articles = (0..30).map { NewsArticle(id, "$it", "$name 文章 $it", "https://example.com/$id/$it") }
            )
        }
    ))
    private var opened: String? = null
    private var searchRequests = 0
    private val refreshed = mutableListOf<String>()

    private fun onPage(sourceId: String, tag: String) = compose.onNode(
        hasTestTag(tag) and hasAnyAncestor(hasTestTag("home_source_page_$sourceId"))
    )

    private fun render() {
        compose.setContent {
            MaterialTheme {
                HomeScreen(
                    state = state.value,
                    contentPadding = PaddingValues(16.dp),
                    onRefreshAll = { refreshed.add("all") },
                    onRefreshSource = { refreshed.add(it) },
                    onOpenArticle = { opened = it },
                    onSelectSource = { state.value = state.value.copy(selectedSourceId = it) },
                    onOpenSearch = { searchRequests++ }
                )
            }
        }
    }

    @Test
    fun sourceTabsFilterCardsAndCardOpensOriginalUrl() {
        render()
        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_two"))
        compose.onNodeWithTag("glass_tab_two").performClick().assertIsSelected()
        onPage("two", "home_article_one_0").assertDoesNotExist()
        onPage("two", "home_article_two_0").assertIsDisplayed().performClick()
        assertEquals("https://example.com/two/0", opened)
        onPage("two", "home_refresh").performClick()
        assertEquals(listOf("two"), refreshed)
    }

    @Test
    fun switchingTabsAndOpeningSearchPreservesBrowsePosition() {
        render()
        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_one"))
        compose.onNodeWithTag("glass_tab_one").performClick()
        onPage("one", "home_feed").performScrollToIndex(16)
        onPage("one", "home_article_one_15").assertIsDisplayed()
        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_two"))
        compose.onNodeWithTag("glass_tab_two").performClick()
        onPage("two", "home_article_two_0").assertIsDisplayed()
        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_one"))
        compose.onNodeWithTag("glass_tab_one").performClick()
        onPage("one", "home_article_one_15").assertIsDisplayed()
        compose.onNodeWithTag("home_search_button").performClick()
        assertEquals(1, searchRequests)
        assertEquals("one", state.value.selectedSourceId)
        compose.onNodeWithTag("home_source_icon").assertIsDisplayed()
        onPage("one", "home_article_one_15").assertIsDisplayed()
        assertEquals(emptyList<String>(), refreshed)
    }

    @Test
    fun swipingSourcesSynchronizesTabsAndIconAndStopsAtEdges() {
        render()
        val pager = compose.onNodeWithTag("home_source_pager")
        pager.performTouchInput { swipeRight() }
        compose.onNodeWithTag("glass_tab_all").assertIsSelected()

        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("glass_tab_one").assertIsSelected()
        compose.onNodeWithTag("home_source_icon").assertContentDescriptionEquals("当前来源：来源一")
        onPage("one", "home_article_one_0").assertIsDisplayed()
        assertEquals("one", state.value.selectedSourceId)

        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("glass_tab_two").assertIsSelected()
        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("glass_tab_two").assertIsSelected()
        assertEquals("two", state.value.selectedSourceId)

        pager.performTouchInput { swipeRight() }
        compose.onNodeWithTag("glass_tab_one").assertIsSelected()
        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_all"))
        compose.onNodeWithTag("glass_tab_all").performClick().assertIsSelected()
        assertEquals(null, state.value.selectedSourceId)
        assertEquals(emptyList<String>(), refreshed)
    }

    @Test
    fun contentPaddingDoesNotShrinkTheScrollableViewport() {
        render()
        val screen = compose.onNodeWithTag("home_screen").fetchSemanticsNode().boundsInRoot
        val pager = compose.onNodeWithTag("home_source_pager").fetchSemanticsNode().boundsInRoot
        val feed = onPage("all", "home_feed").fetchSemanticsNode().boundsInRoot
        assertEquals(screen.top, pager.top, 0.5f)
        assertEquals(screen.bottom, pager.bottom, 0.5f)
        assertEquals(screen.top, feed.top, 0.5f)
        assertEquals(screen.bottom, feed.bottom, 0.5f)
    }
}
