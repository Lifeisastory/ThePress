package com.aster.thepress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aster.core.design_system.R as DesignSystemR
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiquidGlassNavigationDemoTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tabsSwitchContentAndSelectionSurvivesActivityRecreation() {
        val titles = composeRule.activity.resources.getStringArray(DesignSystemR.array.glass_demo_titles)
        val labels = listOf(
            DesignSystemR.string.glass_demo_home,
            DesignSystemR.string.glass_demo_discover,
            DesignSystemR.string.glass_demo_saved
        ).map { composeRule.activity.getString(it) }

        labels.forEachIndexed { index, label ->
            composeRule.onNode(hasText(label) and hasClickAction())
                .performClick()
                .assertIsSelected()
            composeRule.onNodeWithTag("glass_demo_title").assertTextEquals(titles[index])
        }

        composeRule.activityRule.scenario.recreate()

        composeRule.onNode(hasText(labels.last()) and hasClickAction()).assertIsSelected()
        composeRule.onNodeWithTag("glass_demo_title").assertTextEquals(titles.last())
    }

    @Test
    fun lastArticleRemainsAboveFloatingNavigationAfterScrolling() {
        val headlines = composeRule.activity.resources.getStringArray(
            DesignSystemR.array.glass_demo_home_articles
        )
        val homeLabel = composeRule.activity.getString(DesignSystemR.string.glass_demo_home)

        composeRule.onNodeWithTag("glass_demo_feed").performScrollToIndex(headlines.size)
        val lastArticle = composeRule.onNodeWithText(headlines.last()).assertIsDisplayed()
        val homeTab = composeRule.onNode(hasText(homeLabel) and hasClickAction())
            .assertIsDisplayed()
            .assertIsSelected()

        assertTrue(
            "The floating navigation must not cover the last article",
            lastArticle.fetchSemanticsNode().boundsInRoot.bottom <=
                homeTab.fetchSemanticsNode().boundsInRoot.top
        )
    }
}
