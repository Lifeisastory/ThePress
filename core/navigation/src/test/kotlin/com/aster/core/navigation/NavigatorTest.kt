package com.aster.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NavigatorTest {
    private object Home : NavKey
    private object Saved : NavKey
    private object Me : NavKey
    private data class Detail(val id: String) : NavKey

    private lateinit var state: NavigationState
    private lateinit var navigator: Navigator

    @Before
    fun setUp() {
        state = NavigationState(
            startKey = Home,
            topLevelStack = NavBackStack<NavKey>(Home),
            subStacks = listOf<NavKey>(Home, Saved, Me).associateWith { NavBackStack(it) }
        )
        navigator = Navigator(state)
    }

    @Test
    fun tabHistoryHasNoDuplicatesAndBackReturnsToThePreviousTab() {
        navigator.navigate(Saved)
        navigator.navigate(Me)
        navigator.navigate(Saved)
        assertEquals(listOf(Home, Me, Saved), state.topLevelStack.toList())
        navigator.goBack()
        assertEquals(Me, state.currentTopLevelKey)
        navigator.goBack()
        assertEquals(Home, state.currentTopLevelKey)
    }

    @Test
    fun switchingTabsPreservesTheirOwnDetailStacks() {
        val homeDetail = Detail("home")
        val savedDetail = Detail("saved")
        navigator.navigate(homeDetail)
        navigator.navigate(Saved)
        navigator.navigate(savedDetail)
        navigator.navigate(Home)
        assertEquals(homeDetail, state.currentKey)
        assertEquals(listOf(Home), state.topLevelStack.toList())
        navigator.navigate(Saved)
        assertEquals(savedDetail, state.currentKey)
        navigator.goBack()
        assertEquals(Saved, state.currentKey)
        assertEquals(listOf(Home, homeDetail), state.subStacks.getValue(Home).toList())
    }

    @Test
    fun reselectingATabReturnsToItsRootAndRepeatedDetailDoesNotDuplicate() {
        val detail = Detail("1")
        navigator.navigate(Saved)
        navigator.navigate(detail)
        navigator.navigate(detail)
        assertEquals(listOf(Saved, detail), state.currentSubStack.toList())
        navigator.navigate(Saved)
        assertEquals(listOf(Saved), state.currentSubStack.toList())
    }

    @Test
    fun backAtHomeKeepsANonEmptyStartStack() {
        repeat(3) { navigator.goBack() }
        assertEquals(listOf(Home), state.topLevelStack.toList())
        assertEquals(listOf(Home), state.currentSubStack.toList())
    }
}
