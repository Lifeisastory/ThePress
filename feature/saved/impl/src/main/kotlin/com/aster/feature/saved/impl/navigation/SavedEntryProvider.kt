package com.aster.feature.saved.impl.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.aster.feature.saved.api.navigation.SavedNavKey
import com.aster.feature.saved.impl.SavedScreen

fun EntryProviderScope<NavKey>.savedEntry(contentPadding: PaddingValues) {
    entry<SavedNavKey> {
        SavedScreen(contentPadding)
    }
}
