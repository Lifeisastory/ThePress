package com.aster.thepress.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.aster.feature.home.api.navigation.HomeNavKey
import com.aster.feature.me.api.navigation.MeNavKey
import com.aster.feature.saved.api.navigation.SavedNavKey
import com.aster.feature.home.api.R as HomeR
import com.aster.feature.me.api.R as MeR
import com.aster.feature.saved.api.R as SavedR

internal data class TopLevelNavItem(val icon: ImageVector, @param:StringRes val labelRes: Int)

internal val TOP_LEVEL_NAV_ITEMS: Map<NavKey, TopLevelNavItem> = linkedMapOf(
    HomeNavKey to TopLevelNavItem(Icons.Default.Home, HomeR.string.feature_home_title),
    SavedNavKey to TopLevelNavItem(Icons.Default.Favorite, SavedR.string.feature_saved_title),
    MeNavKey to TopLevelNavItem(Icons.Default.Person, MeR.string.feature_me_title)
)
