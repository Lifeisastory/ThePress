package com.aster.thepress

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aster.feature.for_you.api.R as ForYouApiR
import com.aster.feature.me.api.R as MeApiR
import com.aster.feature.digest.api.R as DigestApiR
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiquidGlassNavigationDemoTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun tabLabels() = listOf(
        ForYouApiR.string.feature_for_you_title,
        DigestApiR.string.feature_digest_title,
        MeApiR.string.feature_me_title
    ).map { composeRule.activity.getString(it) }

    private val pageTags = listOf("for_you_page", "digest_page", "profile_page")

    @Test
    fun tabsSwitchContentAndSelectionSurvivesActivityRecreation() {
        val labels = tabLabels()

        labels.forEachIndexed { index, label ->
            composeRule.onNode(hasText(label) and hasClickAction())
                .performClick()
                .assertIsSelected()
            composeRule.onNodeWithTag(pageTags[index]).assertIsDisplayed()
        }

        composeRule.activityRule.scenario.recreate()

        composeRule.onNode(hasText(labels.last()) and hasClickAction()).assertIsSelected()
        composeRule.onNodeWithTag(pageTags.last()).assertIsDisplayed()
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
    fun startingDragKeepsCapsuleLiftedWithBoundedDeformation() {
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
                    "The sample's velocity deformation must stay bounded while pressed",
                    bounds.width * bounds.height in (pressedArea * 0.75f)..(pressedArea * 1.5f)
                )
            }
        } finally {
            tabs.performTouchInput { cancel() }
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun pullingPastEndMovesAndEnlargesSurfaceAndCancelRestoresIt() {
        val tabs = composeRule.onNodeWithTag("liquid_glass_navigation_tabs")
        val surface = composeRule.onNodeWithTag("liquid_glass_navigation_surface")
        val restingBounds = surface.fetchSemanticsNode().boundsInRoot
        tabs.performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width + 100f, centerY))
        }
        composeRule.waitForIdle()
        val movedBounds = surface.fetchSemanticsNode().boundsInRoot
        assertTrue("The surface must move toward the dragged edge", movedBounds.center.x > restingBounds.center.x + 1f)
        assertTrue("Pressing must enlarge the surface", movedBounds.width > restingBounds.width)
        assertTrue("The sample scales the surface uniformly", kotlin.math.abs(
            movedBounds.width / restingBounds.width - movedBounds.height / restingBounds.height
        ) < 0.01f)

        tabs.performTouchInput { cancel() }
        composeRule.waitForIdle()
        val restoredBounds = surface.fetchSemanticsNode().boundsInRoot
        assertTrue("Cancel must restore surface position", kotlin.math.abs(restoredBounds.left - restingBounds.left) < 1f)
        assertTrue("Cancel must restore surface width", kotlin.math.abs(restoredBounds.width - restingBounds.width) < 1f)
        assertTrue("Cancel must restore surface height", kotlin.math.abs(restoredBounds.height - restingBounds.height) < 1f)
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
        composeRule.onNodeWithTag(pageTags.first()).assertIsDisplayed()

        tabs.performTouchInput {
            moveTo(Offset(width * 5f / 6f, centerY))
            up()
        }
        composeRule.onNode(hasText(meLabel) and hasClickAction()).assertIsSelected()
        composeRule.onNodeWithTag(pageTags.last()).assertIsDisplayed()

        tabs.performTouchInput {
            down(Offset(width * 5f / 6f, centerY))
            moveTo(Offset(width / 6f, centerY))
            up()
        }
        composeRule.onNodeWithTag(pageTags.first()).assertIsDisplayed()
    }

    @Test
    fun cancelledDragRestoresSelectionAndIndicator() {
        val forYouLabel = composeRule.activity.getString(ForYouApiR.string.feature_for_you_title)
        val tabs = composeRule.onNodeWithTag("liquid_glass_navigation_tabs")
        val indicator = composeRule.onNodeWithTag("liquid_glass_navigation_indicator")
        val startX = indicator.fetchSemanticsNode().boundsInRoot.center.x

        tabs.performTouchInput {
            down(Offset(width / 6f, centerY))
            moveTo(Offset(width * 5f / 6f, centerY))
            cancel()
        }

        composeRule.onNode(hasText(forYouLabel) and hasClickAction()).assertIsSelected()
        val restoredX = indicator.fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("Cancelled drag must return to the selected tab", kotlin.math.abs(restoredX - startX) < 1f)
    }

    @Test
    fun draggingPastEdgesClampsToFirstAndLastTab() {
        val forYouLabel = composeRule.activity.getString(ForYouApiR.string.feature_for_you_title)
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
        composeRule.onNode(hasText(forYouLabel) and hasClickAction()).assertIsSelected()
    }

    @Test
    fun historyControlRemainsAboveFloatingNavigation() {
        val digestLabel = composeRule.activity.getString(DigestApiR.string.feature_digest_title)
        composeRule.onNode(hasText(digestLabel) and hasClickAction()).performClick()
        composeRule.onNodeWithTag("digest_page").assertIsDisplayed()
        val history = composeRule.onNodeWithTag("digest_history").assertIsDisplayed()
        val digestTab = composeRule.onNode(hasText(digestLabel) and hasClickAction())
            .assertIsDisplayed()
            .assertIsSelected()

        assertTrue(
            "The floating navigation must not cover the history control",
            history.fetchSemanticsNode().boundsInRoot.bottom <=
                digestTab.fetchSemanticsNode().boundsInRoot.top
        )
    }

    @Test
    fun backFromAnotherTabReturnsDirectlyToForYou() {
        val labels = tabLabels()
        composeRule.onNode(hasText(labels[1]) and hasClickAction()).performClick()
        composeRule.onNodeWithTag("digest_page").assertIsDisplayed()
        composeRule.onNode(hasText(labels[2]) and hasClickAction()).performClick()
        composeRule.onNodeWithTag("profile_page").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("for_you_page").assertIsDisplayed()
        composeRule.onNode(hasText(labels[0]) and hasClickAction()).assertIsSelected()
    }
}
