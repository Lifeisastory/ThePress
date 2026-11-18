package com.aster.thepress.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.aster.core.data.repository.NewsRepository
import com.aster.core.navigation.toEntries
import com.aster.feature.search.api.navigation.SearchNavKey
import com.aster.feature.search.impl.navigation.searchEntry
import com.aster.feature.home.impl.navigation.homeEntry
import com.aster.feature.me.impl.navigation.meEntry
import com.aster.feature.saved.impl.navigation.savedEntry
import com.aster.thepress.ui.AppState

@Composable
internal fun AppNavHost(
    appState: AppState,
    repository: NewsRepository,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val provider = entryProvider {
        homeEntry(
            repository = repository,
            contentPadding = contentPadding,
            onOpenSearch = { appState.navigator.navigate(SearchNavKey) }
        )
        searchEntry(
            repository = repository,
            contentPadding = contentPadding,
            onBack = { appState.navigator.goBack() }
        )
        savedEntry(contentPadding)
        meEntry(contentPadding)
    }
    NavDisplay(
        entries = appState.navigationState.toEntries(provider),
        onBack = { appState.navigator.goBack() },
        modifier = modifier
    )
}
