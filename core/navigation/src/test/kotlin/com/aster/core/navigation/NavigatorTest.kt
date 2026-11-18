package com.aster.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NavigatorTest {
    private object Main : NavKey
    private object Search : NavKey
    private data class Detail(val id: String) : NavKey

    private lateinit var state: NavigationState
    private lateinit var navigator: Navigator

    @Before
    fun setUp() {
        state = NavigationState(NavBackStack<NavKey>(Main))
        navigator = Navigator(state)
    }

    @Test
    fun openingSearchPushesASiblingRouteAndBackReturnsToMain() {
        navigator.navigate(Search)
        assertEquals(listOf(Main, Search), state.backStack.toList())
        assertEquals(Search, state.currentKey)
        navigator.goBack()
        assertEquals(listOf(Main), state.backStack.toList())
        assertEquals(Main, state.currentKey)
    }

    @Test
    fun repeatedNavigationToCurrentRouteDoesNotDuplicateIt() {
        repeat(3) { navigator.navigate(Search) }
        assertEquals(listOf(Main, Search), state.backStack.toList())
    }

    @Test
    fun backPopsRoutesInOrder() {
        val detail = Detail("1")
        navigator.navigate(Search)
        navigator.navigate(detail)
        assertEquals(listOf(Main, Search, detail), state.backStack.toList())
        navigator.goBack()
        assertEquals(Search, state.currentKey)
        navigator.goBack()
        assertEquals(Main, state.currentKey)
    }

    @Test
    fun returningToExistingRouteRemovesOnlyEntriesAboveIt() {
        navigator.navigate(Search)
        navigator.navigate(Detail("1"))
        navigator.navigate(Search)
        assertEquals(listOf(Main, Search), state.backStack.toList())
        navigator.navigate(Main)
        assertEquals(listOf(Main), state.backStack.toList())
    }

    @Test
    fun backAtRootKeepsANonEmptyStack() {
        repeat(3) { navigator.goBack() }
        assertEquals(listOf(Main), state.backStack.toList())
        navigator.navigate(Search)
        repeat(3) { navigator.goBack() }
        assertEquals(listOf(Main), state.backStack.toList())
    }
}
