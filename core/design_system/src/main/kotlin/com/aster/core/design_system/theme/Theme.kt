package com.aster.core.design_system.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.aster.core.model.ThemeColor

/** 让原生 View 等非 Compose 内容使用应用实际选择的主题 */
val LocalAppDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    themeColor: ThemeColor = ThemeColor.SYSTEM,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        themeColor == ThemeColor.SYSTEM && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> presetColorScheme(themeColor, darkTheme)
    }

    CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

private fun presetColorScheme(color: ThemeColor, dark: Boolean): androidx.compose.material3.ColorScheme {
    val base = if (dark) DarkColorScheme else LightColorScheme
    val palette = when (color) {
        ThemeColor.BLUE -> if (dark) listOf(0xFFA8C7FA, 0xFF003062, 0xFF084785, 0xFFD6E3FF)
            else listOf(0xFF245FA6, 0xFFFFFFFF, 0xFFD6E3FF, 0xFF001B3C)
        ThemeColor.GREEN -> if (dark) listOf(0xFF82D5B0, 0xFF003827, 0xFF00513A, 0xFF9EF2CB)
            else listOf(0xFF006C4F, 0xFFFFFFFF, 0xFF9EF2CB, 0xFF002114)
        ThemeColor.SYSTEM, ThemeColor.PURPLE -> return base
    }.map { Color(it) }
    return base.copy(
        primary = palette[0], onPrimary = palette[1],
        primaryContainer = palette[2], onPrimaryContainer = palette[3],
        secondary = palette[0], onSecondary = palette[1],
        secondaryContainer = palette[2], onSecondaryContainer = palette[3],
        tertiary = palette[0], onTertiary = palette[1],
        tertiaryContainer = palette[2], onTertiaryContainer = palette[3],
        surfaceTint = palette[0],
        inversePrimary = when (color) {
            ThemeColor.BLUE -> Color(if (dark) 0xFF245FA6 else 0xFFA8C7FA)
            ThemeColor.GREEN -> Color(if (dark) 0xFF006C4F else 0xFF82D5B0)
            else -> base.inversePrimary
        }
    )
}
