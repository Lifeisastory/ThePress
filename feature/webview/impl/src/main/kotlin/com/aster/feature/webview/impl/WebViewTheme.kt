package com.aster.feature.webview.impl

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/** Compose 的配色不会更新 Android Theme；为网页单独提供对应的 isLightTheme 和夜间配置 */
internal fun webViewContext(context: Context, darkTheme: Boolean): Context {
    val style = if (darkTheme) R.style.ThemeOverlay_ThePress_WebView_Dark else R.style.ThemeOverlay_ThePress_WebView_Light
    return ContextThemeWrapper(context, style).apply {
        applyOverrideConfiguration(Configuration().apply {
            uiMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        })
    }
}

internal fun WebView.applyAppTheme(darkTheme: Boolean, backgroundColor: Int) {
    setBackgroundColor(backgroundColor)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, darkTheme)
    }
}
