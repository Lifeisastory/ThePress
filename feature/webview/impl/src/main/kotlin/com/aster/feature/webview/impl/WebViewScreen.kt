package com.aster.feature.webview.impl

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aster.core.design_system.component.SystemInsets
import com.aster.core.design_system.component.AppRefreshLayout
import com.aster.core.design_system.component.statusBarHeight
import com.aster.core.design_system.component.liquid_glass.AppIconButton
import com.aster.core.design_system.component.liquid_glass.AppTitleBar
import com.aster.core.design_system.theme.LocalAppDarkTheme
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebViewScreen(
    url: String,
    onBack: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val state = rememberSaveable(url, saver = WebViewState.saver) { WebViewState(url) }
    val darkTheme = LocalAppDarkTheme.current
    val background = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    val topInset = statusBarHeight()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    // 原生 WebView 不参与离屏背景录制；重复绘制会干扰其硬件渲染
    // 顶栏和刷新指示器共用主题底色，保留玻璃外观但不采样网页
    val backdrop = rememberCanvasBackdrop {
        drawRect(background)
    }
    val dispatcher = remember { NestedScrollDispatcher() }
    val connection = remember { object : NestedScrollConnection {} }
    // 网页保留原生滚动，仅向刷新容器提供当前边界
    val scrollState = remember(state) {
        object : ScrollableState by ScrollableState({ 0f }) {
            override val canScrollBackward: Boolean get() = state.view?.canScrollVertically(-1) == true
            override val canScrollForward: Boolean get() = state.view?.canScrollVertically(1) == true
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val webView = state.view
    DisposableEffect(lifecycle, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) webView?.onPause()
        onDispose { lifecycle.removeObserver(observer) }
    }

    val goBack: () -> Unit = {
        if (state.view?.canGoBack() == true) state.view?.goBack() else onBack()
    }
    val reload: () -> Unit = {
        state.view?.let { view ->
            if (isWebUrl(state.currentUrl)) {
                val retry = state.hasError
                state.hasError = false
                state.isRefreshing = true
                if (retry) view.loadUrl(state.currentUrl) else view.reload()
            }
        }
    }
    BackHandler(enabled = isActive && state.canGoBack, onBack = goBack)

    SystemInsets(
        modifier.fillMaxSize().testTag("webview_screen"),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    ) {
        AppRefreshLayout(
            scrollState = scrollState,
            isRefreshing = state.isRefreshing,
            isLoadingMore = false,
            hasMore = false,
            onRefresh = reload,
            onLoadMore = {},
            enabled = isActive,
            indicatorBackdrop = backdrop,
            indicatorPadding = PaddingValues(top = with(density) { headerHeightPx.toDp() } + 8.dp),
            modifier = Modifier.fillMaxSize().testTag("webview_refresh_layout")
        ) {
            Box(Modifier.fillMaxSize()) {
                key(darkTheme) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize().nestedScroll(connection, dispatcher).testTag("webview_content"),
                        factory = { context ->
                            // 主题变化会重建原生 View；先保存旧实例，保留当前网页与历史
                            val history = state.saveHistory()
                            RefreshWebView(webViewContext(context, darkTheme), dispatcher).apply {
                                applyAppTheme(darkTheme, background.toArgb())
                                // 新闻站点需要脚本与 DOM 存储；不暴露本地文件或原生 JS 接口
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                                        state.progress = newProgress
                                    }

                                    override fun onReceivedTitle(view: WebView, title: String?) {
                                        state.title = title.orEmpty()
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        if (isWebUrl(request.url.toString())) return false
                                        if (request.isForMainFrame && request.hasGesture()) {
                                            Toast.makeText(context, R.string.webview_unsupported_link, Toast.LENGTH_SHORT).show()
                                        }
                                        return true
                                    }

                                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                        state.currentUrl = url
                                        state.title = ""
                                        state.hasError = false
                                        state.progress = 0
                                        state.canGoBack = view.canGoBack()
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                        url?.let { state.currentUrl = it }
                                        state.canGoBack = view.canGoBack()
                                    }

                                    override fun onPageFinished(view: WebView, url: String) {
                                        state.canGoBack = view.canGoBack()
                                        state.isRefreshing = false
                                    }

                                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                        if (request.isForMainFrame) {
                                            state.hasError = true
                                            state.isRefreshing = false
                                        }
                                    }

                                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                        if (request.isForMainFrame) {
                                            state.hasError = true
                                            state.isRefreshing = false
                                        }
                                    }

                                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                        handler.cancel()
                                        if (error.url == state.currentUrl) {
                                            state.hasError = true
                                            state.isRefreshing = false
                                        }
                                    }
                                }
                                state.view = this
                                val restored = history?.let { restoreState(it) }
                                if (restored == null) {
                                    if (isWebUrl(state.currentUrl)) loadUrl(state.currentUrl)
                                    else state.hasError = true
                                } else {
                                    state.canGoBack = canGoBack()
                                    state.title = title.orEmpty()
                                }
                            }
                        },
                        onRelease = { view ->
                            if (state.view === view) {
                                state.savedHistory = state.saveHistory()
                                state.view = null
                            }
                            view.stopLoading()
                            view.webChromeClient = null
                            view.webViewClient = WebViewClient()
                            view.destroy()
                        }
                    )
                }
                if (state.hasError) {
                    Column(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)
                            .testTag("webview_error"),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.webview_load_failed))
                        TextButton(onClick = reload) { Text(stringResource(R.string.webview_retry)) }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().onSizeChanged { headerHeightPx = it.height }
                    .padding(start = 8.dp, end = 8.dp, top = topInset + 8.dp)
                    .testTag("webview_header"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.webview_back),
                    onClick = goBack,
                    backdrop = backdrop,
                    modifier = Modifier.testTag("webview_back")
                )
                AppTitleBar(
                    title = state.title.ifBlank { stringResource(R.string.webview_title) },
                    subtitle = state.currentUrl.toUri().host.orEmpty(),
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f).testTag("webview_title")
                )
                AppIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.webview_close),
                    onClick = onBack,
                    backdrop = backdrop,
                    modifier = Modifier.testTag("webview_close")
                )
            }
            if (!state.hasError && state.progress < 100) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 72.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun isWebUrl(url: String): Boolean {
    val uri = url.toUri()
    return (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) &&
        !uri.host.isNullOrBlank()
}
