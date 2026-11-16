package com.aster.thepress

import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.performTouchInput
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
    fun pressingWithoutDraggingEnlargesCapsuleAndReleaseRestoresIt() {
        val tabs = composeRule.onNodeWithTag("liquid_glass_navigation_tabs")
        val indicator = composeRule.onNodeWithTag("liquid_glass_navigation_indicator")
        val restingHeight = indicator.fetchSemanticsNode().boundsInRoot.height

        tabs.performTouchInput { down(Offset(width / 6f, centerY)) }
        composeRule.waitForIdle()
        assertTrue(
            "A stationary press must enlarge the capsule",
            indicator.fetchSemanticsNode().boundsInRoot.height > restingHeight * 1.04f
        )

        tabs.performTouchInput { up() }
        composeRule.waitForIdle()
        assertTrue(
            "Release must restore the capsule size",
            kotlin.math.abs(indicator.fetchSemanticsNode().boundsInRoot.height - restingHeight) < 1f
        )
    }

    @Test
    fun quickTapStillPlaysCapsuleLift() {
        val tabs = composeRule.onNodeWithTag("liquid_glass_navigation_tabs")
        val indicator = composeRule.onNodeWithTag("liquid_glass_navigation_indicator")
        val restingHeight = indicator.fetchSemanticsNode().boundsInRoot.height

        composeRule.mainClock.autoAdvance = false
        try {
            tabs.performTouchInput {
                down(Offset(width / 6f, centerY))
                up()
            }
            composeRule.mainClock.advanceTimeBy(96)
            assertTrue(
                "A quick tap must not skip the lift animation",
                indicator.fetchSemanticsNode().boundsInRoot.height > restingHeight * 1.02f
            )
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun startingDragKeepsPressedCapsuleArea() {
        val tabs = composeRule.onNodeWithTag("liquid_glass_navigation_tabs")
        val indicator = composeRule.onNodeWithTag("liquid_glass_navigation_indicator")
        tabs.performTouchInput { down(Offset(width / 6f, centerY)) }
        composeRule.waitForIdle()
        val pressedBounds = indicator.fetchSemanticsNode().boundsInRoot
        val pressedArea = pressedBounds.width * pressedBounds.height

        composeRule.mainClock.autoAdvance = false
        try {
            tabs.performTouchInput { moveTo(Offset(width / 3f, centerY)) }
            repeat(12) {
                composeRule.mainClock.advanceTimeByFrame()
                val bounds = indicator.fetchSemanticsNode().boundsInRoot
                assertTrue(
                    "Inertial deformation must preserve the pressed capsule area",
                    bounds.width * bounds.height >= pressedArea * 0.98f
                )
            }
        } finally {
            tabs.performTouchInput { cancel() }
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun pullingPastEndMovesSurfaceWithoutResizingAndCancelRestoresIt() {
        val tabs = composeRule.onNodeWithTag("liquid_glass_navigation_tabs")
        val surface = composeRule.onNodeWithTag("liquid_glass_navigation_surface")
        val restingBounds = surface.fetchSemanticsNode().boundsInRoot
        tabs.performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width + 100f, centerY))
        }
        composeRule.waitForIdle()
        val movedBounds = surface.fetchSemanticsNode().boundsInRoot
        assertTrue("The surface must move toward the dragged edge", movedBounds.left > restingBounds.left + 1f)
        assertTrue("Edge motion must preserve width", kotlin.math.abs(movedBounds.width - restingBounds.width) < 1f)
        assertTrue("Edge motion must preserve height", kotlin.math.abs(movedBounds.height - restingBounds.height) < 1f)

        tabs.performTouchInput { cancel() }
        composeRule.waitForIdle()
        val restoredBounds = surface.fetchSemanticsNode().boundsInRoot
        assertTrue("Cancel must restore surface position", kotlin.math.abs(restoredBounds.left - restingBounds.left) < 1f)
        assertTrue("Cancel must restore surface width", kotlin.math.abs(restoredBounds.width - restingBounds.width) < 1f)
    }

    @Test
    fun enlargedCapsuleExtendsBeyondGlassSurface() {
        val tabs = composeRule.onNodeWithTag("liquid_glass_navigation_tabs")
        val surface = composeRule.onNodeWithTag("liquid_glass_navigation_surface")
        val indicator = composeRule.onNodeWithTag("liquid_glass_navigation_indicator")
        tabs.performTouchInput { down(Offset(width / 6f, centerY)) }
        composeRule.waitForIdle()

        try {
            val surfaceBounds = surface.fetchSemanticsNode().boundsInRoot
            val indicatorBounds = indicator.fetchSemanticsNode().boundsInRoot
            assertTrue("The capsule must extend above the surface", indicatorBounds.top < surfaceBounds.top)
            assertTrue("The capsule must extend below the surface", indicatorBounds.bottom > surfaceBounds.bottom)
        } finally {
            tabs.performTouchInput { cancel() }
        }
    }

    @Test
    fun heldTabFollowsFingerAndCommitsOnlyOnRelease() {
        val titles = composeRule.activity.resources.getStringArray(DesignSystemR.array.glass_demo_titles)
        val savedLabel = composeRule.activity.getString(DesignSystemR.string.glass_demo_saved)
        val tabs = composeRule.onNodeWithTag("liquid_glass_navigation_tabs")
        val indicator = composeRule.onNodeWithTag("liquid_glass_navigation_indicator")
        val startX = indicator.fetchSemanticsNode().boundsInRoot.center.x
        val tabWidth = tabs.fetchSemanticsNode().boundsInRoot.width / 3f

        tabs.performTouchInput {
            down(Offset(width / 6f, centerY))
            advanceEventTime(600)
            moveTo(Offset(width / 3f, centerY))
        }
        composeRule.waitForIdle()
        val draggedX = indicator.fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("The capsule must follow between tabs", draggedX > startX + tabWidth * 0.3f)
        assertTrue("The capsule must not jump to the next tab", draggedX < startX + tabWidth * 0.7f)
        composeRule.onNodeWithTag("glass_demo_title").assertTextEquals(titles.first())

        tabs.performTouchInput {
            moveTo(Offset(width * 5f / 6f, centerY))
            up()
        }
        composeRule.onNode(hasText(savedLabel) and hasClickAction()).assertIsSelected()
        composeRule.onNodeWithTag("glass_demo_title").assertTextEquals(titles.last())

        tabs.performTouchInput {
            down(Offset(width * 5f / 6f, centerY))
            moveTo(Offset(width / 6f, centerY))
            up()
        }
        composeRule.onNodeWithTag("glass_demo_title").assertTextEquals(titles.first())
    }

    @Test
    fun cancelledDragRestoresSelectionAndIndicator() {
        val homeLabel = composeRule.activity.getString(DesignSystemR.string.glass_demo_home)
        val tabs = composeRule.onNodeWithTag("liquid_glass_navigation_tabs")
        val indicator = composeRule.onNodeWithTag("liquid_glass_navigation_indicator")
        val startX = indicator.fetchSemanticsNode().boundsInRoot.center.x

        tabs.performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width * 5f / 6f, centerY))
            cancel()
        }

        composeRule.onNode(hasText(homeLabel) and hasClickAction()).assertIsSelected()
        val restoredX = indicator.fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("Cancelled drag must return to the selected tab", kotlin.math.abs(restoredX - startX) < 1f)
    }

    @Test
    fun draggingPastEdgesClampsToFirstAndLastTab() {
        val homeLabel = composeRule.activity.getString(DesignSystemR.string.glass_demo_home)
        val savedLabel = composeRule.activity.getString(DesignSystemR.string.glass_demo_saved)
        val tabs = composeRule.onNodeWithTag("liquid_glass_navigation_tabs")

        tabs.performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width + 100f, centerY))
            up()
        }
        composeRule.onNode(hasText(savedLabel) and hasClickAction()).assertIsSelected()
        tabs.performTouchInput {
            down(Offset(width * 5f / 6f, centerY))
            moveTo(Offset(-100f, centerY))
            up()
        }
        composeRule.onNode(hasText(homeLabel) and hasClickAction()).assertIsSelected()
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
