package com.aster.feature.me.impl.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.aster.feature.me.api.navigation.MeNavKey
import com.aster.feature.me.impl.MeScreen

fun EntryProviderScope<NavKey>.meEntry(contentPadding: PaddingValues) {
    entry<MeNavKey> {
        MeScreen(contentPadding)
    }
}
