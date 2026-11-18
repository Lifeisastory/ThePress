package com.aster.feature.main.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.aster.core.data.repository.NewsRepository
import com.aster.feature.main.api.navigation.MainNavKey
import com.aster.feature.main.impl.MainScreen

fun EntryProviderScope<NavKey>.mainEntry(
    repository: NewsRepository,
    onOpenSearch: () -> Unit,
    isActive: () -> Boolean
) {
    entry<MainNavKey> {
        MainScreen(
            repository = repository,
            onOpenSearch = onOpenSearch,
            isActive = isActive()
        )
    }
}
