package com.aster.thepress.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aster.core.data.repository.NewsRepository
import com.aster.core.data.repository.UserPreferencesRepository
import com.aster.core.data.repository.DigestSettingsRepository
import com.aster.core.data.repository.DigestAgentRepository
import com.aster.thepress.navigation.AppNavHost

@Composable
internal fun App(
    repository: NewsRepository,
    preferencesRepository: UserPreferencesRepository,
    digestSettingsRepository: DigestSettingsRepository,
    digestAgentRepository: DigestAgentRepository,
    modifier: Modifier = Modifier,
    appState: AppState = rememberAppState()
) {
    AppNavHost(
        appState = appState,
        repository = repository,
        preferencesRepository = preferencesRepository,
        digestSettingsRepository = digestSettingsRepository,
        digestAgentRepository = digestAgentRepository,
        modifier = modifier.fillMaxSize()
    )
}
