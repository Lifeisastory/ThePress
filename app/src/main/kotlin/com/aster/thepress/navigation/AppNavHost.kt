package com.aster.thepress.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.aster.core.data.repository.NewsRepository
import com.aster.core.data.repository.UserPreferencesRepository
import com.aster.core.navigation.toEntries
import com.aster.feature.main.api.navigation.MainNavKey
import com.aster.feature.main.impl.navigation.mainEntry
import com.aster.feature.search.api.navigation.SearchNavKey
import com.aster.feature.search.impl.navigation.searchEntry
import com.aster.thepress.ui.AppState

@Composable
internal fun AppNavHost(
    appState: AppState,
    repository: NewsRepository,
    preferencesRepository: UserPreferencesRepository,
    modifier: Modifier = Modifier
) {
    val provider = entryProvider {
        mainEntry(
            repository = repository,
            preferencesRepository = preferencesRepository,
            onOpenSearch = { appState.navigator.navigate(SearchNavKey) },
            isActive = { appState.navigationState.currentKey == MainNavKey }
        )
        searchEntry(
            repository = repository,
            onBack = { appState.navigator.goBack() }
        )
    }
    NavDisplay(
        entries = appState.navigationState.toEntries(provider),
        onBack = { appState.navigator.goBack() },
        modifier = modifier
    )
}
