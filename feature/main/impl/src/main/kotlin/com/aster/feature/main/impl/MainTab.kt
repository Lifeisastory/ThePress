package com.aster.feature.main.impl

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.aster.feature.home.api.R as HomeR
import com.aster.feature.me.api.R as MeR
import com.aster.feature.saved.api.R as SavedR

/** Main 内部页面标识，不参与应用路由 */
internal enum class MainTab(val icon: ImageVector, @param:StringRes val labelRes: Int) {
    Home(Icons.Default.Home, HomeR.string.feature_home_title),
    Saved(Icons.Default.Favorite, SavedR.string.feature_saved_title),
    Profile(Icons.Default.Person, MeR.string.feature_me_title)
}
