package com.aster.feature.home.impl

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
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
        compose.onNodeWithTag("home_article_one_0").assertDoesNotExist()
        compose.onNodeWithTag("home_article_two_0").assertIsDisplayed().performClick()
        assertEquals("https://example.com/two/0", opened)
        compose.onNodeWithTag("home_refresh").performClick()
        assertEquals(listOf("two"), refreshed)
    }

    @Test
    fun switchingTabsAndOpeningSearchPreservesBrowsePosition() {
        render()
        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_one"))
        compose.onNodeWithTag("glass_tab_one").performClick()
        compose.onNodeWithTag("home_feed").performScrollToIndex(16)
        compose.onNodeWithTag("home_article_one_15").assertIsDisplayed()
        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_two"))
        compose.onNodeWithTag("glass_tab_two").performClick()
        compose.onNodeWithTag("home_article_two_0").assertIsDisplayed()
        compose.onNodeWithTag("home_source_tabs").performScrollToNode(hasTestTag("glass_tab_one"))
        compose.onNodeWithTag("glass_tab_one").performClick()
        compose.onNodeWithTag("home_article_one_15").assertIsDisplayed()
        compose.onNodeWithTag("home_search_button").performClick()
        assertEquals(1, searchRequests)
        assertEquals("one", state.value.selectedSourceId)
        compose.onNodeWithTag("home_source_icon").assertIsDisplayed()
        compose.onNodeWithTag("home_article_one_15").assertIsDisplayed()
        assertEquals(emptyList<String>(), refreshed)
    }
}
