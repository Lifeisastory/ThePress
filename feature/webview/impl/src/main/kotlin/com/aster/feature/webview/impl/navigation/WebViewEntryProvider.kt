package com.aster.feature.webview.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.aster.feature.webview.api.navigation.WebViewNavKey
import com.aster.feature.webview.impl.WebViewScreen

fun EntryProviderScope<NavKey>.webViewEntry(
    onBack: () -> Unit,
    isActive: (WebViewNavKey) -> Boolean
) {
    entry<WebViewNavKey> { key ->
        WebViewScreen(url = key.url, onBack = onBack, isActive = isActive(key))
    }
}
