package com.aster.thepress

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.aster.core.design_system.demo.LiquidGlassNavigationDemo
import com.aster.core.design_system.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                LiquidGlassNavigationDemo()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LiquidGlassNavigationPreview() {
    AppTheme {
        LiquidGlassNavigationDemo()
    }
}
