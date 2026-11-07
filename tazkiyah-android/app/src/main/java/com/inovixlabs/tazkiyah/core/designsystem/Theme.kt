package com.inovixlabs.tazkiyah.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Brand colors always; dynamic (wallpaper) color is intentionally not used. */
@Composable
fun TazkiyahTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = TazkiyahTypography,
        content = content,
    )
}

@Composable
fun isAppInDarkTheme(): Boolean = MaterialTheme.colorScheme.background == DarkColors.background
