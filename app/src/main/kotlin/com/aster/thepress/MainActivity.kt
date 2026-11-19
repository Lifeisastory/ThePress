package com.aster.thepress

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aster.core.model.AppearanceSettings
import com.aster.core.model.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import com.aster.core.design_system.theme.AppTheme
import com.aster.thepress.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 三键导航模式也透出页面背景，固定控件与滚动内容分别处理安全间距
            window.isNavigationBarContrastEnforced = false
        }
        val data = (application as Application).dataContainer
        setContent {
            val settingsFlow = remember(data) {
                data.userPreferencesRepository.appearance.retryWhen { _, _ -> delay(3000); true }
            }
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppearanceSettings())
            val darkTheme = when (settings.mode) {
                ThemeMode.SYSTEM -> LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                val bars = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
            }
            AppTheme(darkTheme = darkTheme, themeColor = settings.color) {
                App(repository = data.newsRepository, preferencesRepository = data.userPreferencesRepository,
                    digestSettingsRepository = data.digestSettingsRepository,
                    digestAgentRepository = data.digestAgentRepository)
            }
        }
    }
}
