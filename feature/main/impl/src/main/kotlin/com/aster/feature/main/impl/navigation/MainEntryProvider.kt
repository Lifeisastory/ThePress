package com.aster.feature.main.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.aster.core.data.repository.NewsRepository
import com.aster.core.data.repository.UserPreferencesRepository
import com.aster.feature.main.api.navigation.MainNavKey
import com.aster.feature.main.impl.MainScreen

fun EntryProviderScope<NavKey>.mainEntry(
    repository: NewsRepository,
    preferencesRepository: UserPreferencesRepository,
    onOpenSearch: () -> Unit,
    isActive: () -> Boolean
) {
    entry<MainNavKey> {
        MainScreen(
            repository = repository,
            preferencesRepository = preferencesRepository,
            onOpenSearch = onOpenSearch,
            isActive = isActive()
        )
    }
}
