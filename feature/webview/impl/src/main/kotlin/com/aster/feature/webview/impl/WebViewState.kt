package com.aster.feature.webview.impl

import android.os.Bundle
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/** 只将网页历史保存到路由状态；WebView 实例由 AndroidView 创建和释放 */
internal class WebViewState(
    initialUrl: String,
    var savedHistory: Bundle? = null
) {
    var view: WebView? by mutableStateOf(null)
    var currentUrl by mutableStateOf(initialUrl)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var canGoBack by mutableStateOf(false)
    var hasError by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)

    fun saveHistory(): Bundle? = view?.let { webView ->
        Bundle().also { webView.saveState(it) }
    } ?: savedHistory

    companion object {
        val saver = Saver<WebViewState, Bundle>(
            save = { state ->
                Bundle().apply {
                    putString("url", state.currentUrl)
                    putBundle("history", state.saveHistory())
                }
            },
            restore = { WebViewState(it.getString("url").orEmpty(), it.getBundle("history")) }
        )
    }
}
