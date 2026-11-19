package com.aster.feature.main.impl

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.aster.core.design_system.component.AppBriefingIcon
import com.aster.feature.digest.api.R as DigestR
import com.aster.feature.for_you.api.R as ForYouR
import com.aster.feature.me.api.R as MeR

/** Main 内部页面标识，不参与应用路由 */
internal enum class MainTab(val icon: ImageVector, @param:StringRes val labelRes: Int) {
    ForYou(Icons.Default.Star, ForYouR.string.feature_for_you_title),
    Digest(AppBriefingIcon, DigestR.string.feature_digest_title),
    Profile(Icons.Default.Person, MeR.string.feature_me_title)
}
