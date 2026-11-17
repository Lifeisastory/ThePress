package com.aster.thepress.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aster.core.navigation.NavigationState
import com.aster.core.navigation.Navigator
import com.aster.core.navigation.rememberNavigationState
import com.aster.feature.home.api.navigation.HomeNavKey
import com.aster.thepress.navigation.TOP_LEVEL_NAV_ITEMS

@Composable
internal fun rememberAppState(): AppState {
    val navigationState = rememberNavigationState(HomeNavKey, TOP_LEVEL_NAV_ITEMS.keys)
    return remember(navigationState) { AppState(navigationState) }
}

internal class AppState(val navigationState: NavigationState) {
    val navigator = Navigator(navigationState)
}
