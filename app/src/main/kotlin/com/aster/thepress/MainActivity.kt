package com.aster.thepress

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aster.core.design_system.theme.AppTheme
import com.aster.thepress.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as Application).newsRepository
        setContent {
            AppTheme {
                App(repository = repository)
            }
        }
    }
}
