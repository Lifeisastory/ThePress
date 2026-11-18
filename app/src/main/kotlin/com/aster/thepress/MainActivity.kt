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
            // 三键导航模式也透出页面背景，内容安全区由 AppImmersiveBox 处理
            window.isNavigationBarContrastEnforced = false
        }
        val repository = (application as Application).newsRepository
        setContent {
            AppTheme {
                App(repository = repository)
            }
        }
    }
}
