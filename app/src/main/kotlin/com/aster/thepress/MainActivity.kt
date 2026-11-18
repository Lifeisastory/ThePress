package com.aster.thepress

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
            AppTheme {
                App(repository = data.newsRepository, preferencesRepository = data.userPreferencesRepository)
            }
        }
    }
}
