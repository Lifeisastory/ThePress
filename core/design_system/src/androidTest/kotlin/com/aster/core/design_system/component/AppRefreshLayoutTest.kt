package com.aster.core.design_system.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.aster.core.design_system.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppRefreshLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val refreshing = mutableStateOf(false)
    private val loadingMore = mutableStateOf(false)
    private val hasMore = mutableStateOf(true)
    private val enabled = mutableStateOf(true)
    private var refreshCalls = 0
    private var loadCalls = 0
    private lateinit var listState: LazyListState

    private fun render(count: Int = 40) {
        compose.setContent {
            listState = rememberLazyListState()
            MaterialTheme {
                AppRefreshLayout(
                    scrollState = listState,
                    isRefreshing = refreshing.value,
                    isLoadingMore = loadingMore.value,
                    hasMore = hasMore.value,
                    onRefresh = { refreshCalls++; refreshing.value = true },
                    onLoadMore = { loadCalls++; loadingMore.value = true },
                    modifier = Modifier.size(300.dp, 400.dp).testTag("container"),
                    enabled = enabled.value,
                    indicatorPadding = PaddingValues(16.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("list")
                    ) {
                        items(count, key = { it }) { index ->
                            Text("Item $index", Modifier.fillMaxWidth().height(56.dp))
                        }
                    }
                }
            }
        }
    }

    private fun onLabel(@StringRes labelRes: Int) = compose.onNodeWithText(
        InstrumentationRegistry.getInstrumentation().targetContext.getString(labelRes)
    )

    private fun pullDown() = compose.onNodeWithTag("list").performTouchInput {
        swipeDown(startY = height * 0.15f, endY = height * 0.9f, durationMillis = 600)
    }

    private fun pullUp() = compose.onNodeWithTag("list").performTouchInput {
        swipeUp(startY = height * 0.9f, endY = height * 0.15f, durationMillis = 600)
    }

    @Test
    fun refreshIsControlledByCallerAndCanRetryAfterCompletion() {
        render()
        pullDown()
        onLabel(R.string.app_refresh_refreshing).assertIsDisplayed()
        pullDown()
        compose.runOnIdle { assertEquals(1, refreshCalls); refreshing.value = false }
        pullDown()
        compose.runOnIdle { assertEquals(2, refreshCalls); assertEquals(0, loadCalls) }
    }

    @Test
    fun bottomPullLoadsOnceAndLoadingBlocksRefresh() {
        render()
        compose.onNodeWithTag("list").performScrollToIndex(39)
        pullUp()
        onLabel(R.string.app_refresh_loading_more).assertIsDisplayed()
        pullUp()
        compose.onNodeWithTag("list").performScrollToIndex(0)
        pullDown()
        compose.runOnIdle { assertEquals(1, loadCalls); assertEquals(0, refreshCalls) }
    }

    @Test
    fun scrollingAndFlingInsideListDoNotRequestAnything() {
        render()
        compose.onNodeWithTag("list").performScrollToIndex(15)
        pullDown()
        compose.onNodeWithTag("list").performScrollToIndex(39)
        compose.runOnIdle { assertEquals(0, refreshCalls); assertEquals(0, loadCalls) }
    }

    @Test
    fun cancelledPullAndPullBelowThresholdDoNotRefresh() {
        render()
        val list = compose.onNodeWithTag("list")
        list.performTouchInput {
            down(Offset(centerX, height * 0.15f))
            moveTo(Offset(centerX, height * 0.9f))
        }
        onLabel(R.string.app_refresh_release).assertIsDisplayed()
        list.performTouchInput { cancel() }
        list.performTouchInput {
            down(center)
            moveBy(Offset(0f, 30f))
            up()
        }
        compose.runOnIdle { assertEquals(0, refreshCalls); assertEquals(0, loadCalls) }
    }

    @Test
    fun reversingPullBeforeReleaseDoesNotRefresh() {
        render()
        val list = compose.onNodeWithTag("list")
        list.performTouchInput {
            down(Offset(centerX, height * 0.15f))
            moveTo(Offset(centerX, height * 0.9f))
        }
        onLabel(R.string.app_refresh_release).assertIsDisplayed()
        list.performTouchInput {
            moveTo(Offset(centerX, height * 0.15f + 30f))
            up()
        }
        compose.runOnIdle { assertEquals(0, refreshCalls); assertEquals(0, loadCalls) }
    }

    @Test
    fun disablingAnArmedGestureCancelsItUntilNextTouch() {
        render()
        val list = compose.onNodeWithTag("list")
        list.performTouchInput {
            down(Offset(centerX, height * 0.15f))
            moveTo(Offset(centerX, height * 0.9f))
        }
        onLabel(R.string.app_refresh_release).assertIsDisplayed()
        compose.runOnIdle { enabled.value = false }
        compose.runOnIdle { enabled.value = true }
        list.performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, refreshCalls) }
        pullDown()
        compose.runOnIdle { assertEquals(1, refreshCalls) }
    }

    @Test
    fun startingAnotherRequestCancelsAnArmedLoadGesture() {
        render()
        val list = compose.onNodeWithTag("list")
        list.performScrollToIndex(39)
        list.performTouchInput {
            down(Offset(centerX, height * 0.9f))
            moveTo(Offset(centerX, height * 0.15f))
        }
        onLabel(R.string.app_refresh_release_load).assertIsDisplayed()
        compose.runOnIdle { refreshing.value = true }
        compose.runOnIdle { refreshing.value = false }
        list.performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, loadCalls); assertEquals(0, refreshCalls) }
        pullUp()
        compose.runOnIdle { assertEquals(1, loadCalls) }
    }

    @Test
    fun exhaustedListShowsMessageWithoutCallingLoadMore() {
        hasMore.value = false
        render()
        val list = compose.onNodeWithTag("list")
        list.performScrollToIndex(39)
        list.performTouchInput {
            down(Offset(centerX, height * 0.9f))
            moveTo(Offset(centerX, height * 0.15f))
        }
        onLabel(R.string.app_refresh_no_more).assertIsDisplayed()
        list.performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, loadCalls) }
    }

    @Test
    fun emptyListCanStillRefresh() {
        render(count = 0)
        pullDown()
        compose.runOnIdle { assertEquals(1, refreshCalls) }
    }

    @Test
    fun shortListCanLoadMoreInTheUpwardDirection() {
        render(count = 1)
        pullUp()
        compose.runOnIdle { assertEquals(1, loadCalls); assertEquals(0, refreshCalls) }
    }

    @Test
    fun indicatorPaddingDoesNotShrinkListViewport() {
        render()
        val container = compose.onNodeWithTag("container").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("list").fetchSemanticsNode().boundsInRoot
        assertEquals(container, list)
    }
}
