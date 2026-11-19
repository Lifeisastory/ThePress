package com.aster.feature.for_you.impl

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
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.aster.core.model.NewsArticle
import com.aster.core.model.NewsSource
import com.aster.feature.for_you.impl.model.ForYouUiState
import com.aster.feature.for_you.impl.model.SourceFeedUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ForYouPageTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = mutableStateOf(ForYouUiState(
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
        hasTestTag(tag) and hasAnyAncestor(hasTestTag("for_you_source_page_$sourceId"))
    )

    private fun render() {
        compose.setContent {
            MaterialTheme {
                ForYouPage(
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
        compose.onNodeWithTag("for_you_source_tabs").performScrollToNode(hasTestTag("glass_tab_two"))
        compose.onNodeWithTag("glass_tab_two").performClick().assertIsSelected()
        onPage("two", "for_you_article_one_0").assertDoesNotExist()
        onPage("two", "for_you_article_two_0").assertIsDisplayed().performClick()
        assertEquals("https://example.com/two/0", opened)
    }

    @Test
    fun switchingTabsAndOpeningSearchPreservesBrowsePosition() {
        render()
        compose.onNodeWithTag("for_you_source_tabs").performScrollToNode(hasTestTag("glass_tab_one"))
        compose.onNodeWithTag("glass_tab_one").performClick()
        onPage("one", "for_you_feed").performScrollToIndex(15)
        onPage("one", "for_you_article_one_15").assertIsDisplayed()
        compose.onNodeWithTag("for_you_source_tabs").performScrollToNode(hasTestTag("glass_tab_two"))
        compose.onNodeWithTag("glass_tab_two").performClick()
        onPage("two", "for_you_article_two_0").assertIsDisplayed()
        compose.onNodeWithTag("for_you_source_tabs").performScrollToNode(hasTestTag("glass_tab_one"))
        compose.onNodeWithTag("glass_tab_one").performClick()
        onPage("one", "for_you_article_one_15").assertIsDisplayed()
        compose.onNodeWithTag("for_you_search_button").performClick()
        assertEquals(1, searchRequests)
        assertEquals("one", state.value.selectedSourceId)
        compose.onNodeWithTag("for_you_source_icon").assertIsDisplayed()
        onPage("one", "for_you_article_one_15").assertIsDisplayed()
        assertEquals(emptyList<String>(), refreshed)
    }

    @Test
    fun swipingSourcesSynchronizesTabsAndIconAndStopsAtEdges() {
        render()
        val pager = compose.onNodeWithTag("for_you_source_pager")
        pager.performTouchInput { swipeRight() }
        compose.onNodeWithTag("glass_tab_all").assertIsSelected()

        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("glass_tab_one").assertIsSelected()
        compose.onNodeWithTag("for_you_source_icon").assertContentDescriptionEquals(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(
                R.string.for_you_current_source, state.value.feeds.first().source.name
            )
        )
        onPage("one", "for_you_article_one_0").assertIsDisplayed()
        assertEquals("one", state.value.selectedSourceId)

        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("glass_tab_two").assertIsSelected()
        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("glass_tab_two").assertIsSelected()
        assertEquals("two", state.value.selectedSourceId)

        pager.performTouchInput { swipeRight() }
        compose.onNodeWithTag("glass_tab_one").assertIsSelected()
        compose.onNodeWithTag("for_you_source_tabs").performScrollToNode(hasTestTag("glass_tab_all"))
        compose.onNodeWithTag("glass_tab_all").performClick().assertIsSelected()
        assertEquals(null, state.value.selectedSourceId)
        assertEquals(emptyList<String>(), refreshed)
    }

    @Test
    fun pullRefreshTargetsCurrentSourceAndIgnoresOtherSourcesLoading() {
        render()
        onPage("all", "for_you_feed").performTouchInput {
            swipeDown(startY = height * 0.4f, endY = height * 0.95f, durationMillis = 600)
        }
        compose.runOnIdle { assertEquals(listOf("all"), refreshed) }

        compose.onNodeWithTag("for_you_source_tabs").performScrollToNode(hasTestTag("glass_tab_two"))
        compose.onNodeWithTag("glass_tab_two").performClick()
        compose.runOnIdle {
            state.value = state.value.copy(feeds = state.value.feeds.map {
                it.copy(isLoading = it.source.id == "one")
            })
        }
        onPage("two", "for_you_feed").performTouchInput {
            swipeDown(startY = height * 0.4f, endY = height * 0.95f, durationMillis = 600)
        }
        compose.runOnIdle { assertEquals(listOf("all", "two"), refreshed) }
    }

    @Test
    fun contentPaddingDoesNotShrinkTheScrollableViewport() {
        render()
        val screen = compose.onNodeWithTag("for_you_page").fetchSemanticsNode().boundsInRoot
        val pager = compose.onNodeWithTag("for_you_source_pager").fetchSemanticsNode().boundsInRoot
        val feed = onPage("all", "for_you_feed").fetchSemanticsNode().boundsInRoot
        assertEquals(screen.top, pager.top, 0.5f)
        assertEquals(screen.bottom, pager.bottom, 0.5f)
        assertEquals(screen.top, feed.top, 0.5f)
        assertEquals(screen.bottom, feed.bottom, 0.5f)
    }
}
