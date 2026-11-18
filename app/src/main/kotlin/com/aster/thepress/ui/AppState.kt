package com.aster.thepress.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aster.core.navigation.NavigationState
import com.aster.core.navigation.Navigator
import com.aster.core.navigation.rememberNavigationState
import com.aster.feature.main.api.navigation.MainNavKey

@Composable
internal fun rememberAppState(): AppState {
    val navigationState = rememberNavigationState(MainNavKey)
    return remember(navigationState) { AppState(navigationState) }
}

internal class AppState(val navigationState: NavigationState) {
    val navigator = Navigator(navigationState)
}
