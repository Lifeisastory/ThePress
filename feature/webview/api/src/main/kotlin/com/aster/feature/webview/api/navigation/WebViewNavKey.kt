package com.aster.feature.webview.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class WebViewNavKey(val url: String) : NavKey
