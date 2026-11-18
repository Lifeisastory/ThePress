package com.aster.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ThemeColor { SYSTEM, PURPLE, BLUE, GREEN }

data class AppearanceSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val color: ThemeColor = ThemeColor.SYSTEM
)

