package com.aster.thepress

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aster.feature.home.api.R as HomeApiR
import com.aster.feature.home.impl.R as HomeImplR
import com.aster.feature.me.api.R as MeApiR
import com.aster.feature.saved.api.R as SavedApiR
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiquidGlassNavigationDemoTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun tabLabels() = listOf(
        HomeApiR.string.feature_home_title,
        SavedApiR.string.feature_saved_title,
        MeApiR.string.feature_me_title
    ).map { composeRule.activity.getString(it) }

    private fun pageTitles() = listOf(
        HomeImplR.string.home_title,
        SavedApiR.string.feature_saved_title,
        MeApiR.string.feature_me_title
    ).map { composeRule.activity.getString(it) }

    @Test
    fun tabsSwitchContentAndSelectionSurvivesActivityRecreation() {
        val titles = pageTitles()
        val labels = tabLabels()

        labels.forEachIndexed { index, label ->
            composeRule.onNode(hasText(label) and hasClickAction())
                .performClick()
                .assertIsSelected()
            composeRule.onNodeWithTag("page_title").assertTextEquals(titles[index])
        }

        composeRule.activityRule.scenario.recreate()

        composeRule.onNode(hasText(labels.last()) and hasClickAction()).assertIsSelected()
        composeRule.onNodeWithTag("page_title").assertTextEquals(titles.last())
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
        val titles = pageTitles()
        val meLabel = composeRule.activity.getString(MeApiR.string.feature_me_title)
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
        composeRule.onNodeWithTag("page_title").assertTextEquals(titles.first())

        tabs.performTouchInput {
            moveTo(Offset(width * 5f / 6f, centerY))
            up()
        }
        composeRule.onNode(hasText(meLabel) and hasClickAction()).assertIsSelected()
        composeRule.onNodeWithTag("page_title").assertTextEquals(titles.last())

        tabs.performTouchInput {
            down(Offset(width * 5f / 6f, centerY))
            moveTo(Offset(width / 6f, centerY))
            up()
        }
        composeRule.onNodeWithTag("page_title").assertTextEquals(titles.first())
    }

    @Test
    fun cancelledDragRestoresSelectionAndIndicator() {
        val homeLabel = composeRule.activity.getString(HomeApiR.string.feature_home_title)
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
        val homeLabel = composeRule.activity.getString(HomeApiR.string.feature_home_title)
        val meLabel = composeRule.activity.getString(MeApiR.string.feature_me_title)
        val tabs = composeRule.onNodeWithTag("liquid_glass_navigation_tabs")

        tabs.performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width + 100f, centerY))
            up()
        }
        composeRule.onNode(hasText(meLabel) and hasClickAction()).assertIsSelected()
        tabs.performTouchInput {
            down(Offset(width * 5f / 6f, centerY))
            moveTo(Offset(-100f, centerY))
            up()
        }
        composeRule.onNode(hasText(homeLabel) and hasClickAction()).assertIsSelected()
    }

    @Test
    fun placeholderRemainsAboveFloatingNavigation() {
        val savedLabel = composeRule.activity.getString(SavedApiR.string.feature_saved_title)
        composeRule.onNode(hasText(savedLabel) and hasClickAction()).performClick()
        composeRule.onNodeWithTag("saved_screen").assertIsDisplayed()
        val title = composeRule.onNodeWithTag("page_title").assertIsDisplayed()
        val savedTab = composeRule.onNode(hasText(savedLabel) and hasClickAction())
            .assertIsDisplayed()
            .assertIsSelected()

        assertTrue(
            "The floating navigation must not cover the placeholder",
            title.fetchSemanticsNode().boundsInRoot.bottom <=
                savedTab.fetchSemanticsNode().boundsInRoot.top
        )
    }

    @Test
    fun backReturnsThroughPreviouslySelectedTabs() {
        val labels = tabLabels()
        composeRule.onNode(hasText(labels[1]) and hasClickAction()).performClick()
        composeRule.onNodeWithTag("saved_screen").assertIsDisplayed()
        composeRule.onNode(hasText(labels[2]) and hasClickAction()).performClick()
        composeRule.onNodeWithTag("me_screen").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("saved_screen").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNode(hasText(labels[0]) and hasClickAction()).assertIsSelected()
    }
}
