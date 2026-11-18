package com.aster.feature.search.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.aster.core.data.repository.NewsRepository
import com.aster.feature.search.api.navigation.SearchNavKey
import com.aster.feature.search.impl.SearchRoute

fun EntryProviderScope<NavKey>.searchEntry(
    repository: NewsRepository,
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit
) {
    entry<SearchNavKey> {
        SearchRoute(repository, onBack, onOpenArticle)
    }
}
