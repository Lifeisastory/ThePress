package com.aster.feature.home.impl.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.aster.core.data.repository.NewsRepository
import com.aster.feature.home.api.navigation.HomeNavKey
import com.aster.feature.home.impl.HomeRoute

fun EntryProviderScope<NavKey>.homeEntry(
    repository: NewsRepository,
    contentPadding: PaddingValues,
    onOpenSearch: () -> Unit
) {
    entry<HomeNavKey> {
        HomeRoute(repository, contentPadding, onOpenSearch)
    }
}
