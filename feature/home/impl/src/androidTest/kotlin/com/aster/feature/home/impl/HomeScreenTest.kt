package com.aster.feature.home.impl

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
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
                    onQueryChange = { state.value = state.value.copy(query = it) },
                    onSelectSource = { state.value = state.value.copy(selectedSourceId = it) },
                    onRetrySearch = {}
                )
            }
        }
    }

    @Test
    fun sourceTabsFilterCardsAndCardOpensOriginalUrl() {
        render()
        compose.onNodeWithTag("home_tab_two").performClick().assertIsSelected()
        compose.onNodeWithTag("home_article_one_0").assertDoesNotExist()
        compose.onNodeWithTag("home_article_two_0").assertIsDisplayed().performClick()
        assertEquals("https://example.com/two/0", opened)
        compose.onNodeWithTag("home_refresh").performClick()
        assertEquals(listOf("two"), refreshed)
    }

    @Test
    fun switchingTabsAndClearingSearchRestoresBrowsePosition() {
        render()
        compose.onNodeWithTag("home_tab_one").performClick()
        compose.onNodeWithTag("home_feed").performScrollToIndex(16)
        compose.onNodeWithTag("home_article_one_15").assertIsDisplayed()
        compose.onNodeWithTag("home_tab_two").performClick()
        compose.onNodeWithTag("home_article_two_0").assertIsDisplayed()
        compose.onNodeWithTag("home_tab_one").performClick()
        compose.onNodeWithTag("home_article_one_15").assertIsDisplayed()
        compose.onNodeWithTag("home_search").performTextInput("无结果")
        compose.onNodeWithText("未找到匹配内容，可更换关键词或切换到“全部”。").assertIsDisplayed()
        compose.onNodeWithText("清空").performClick()
        compose.onNodeWithTag("home_article_one_15").assertIsDisplayed()
        assertEquals(emptyList<String>(), refreshed)
    }
}
