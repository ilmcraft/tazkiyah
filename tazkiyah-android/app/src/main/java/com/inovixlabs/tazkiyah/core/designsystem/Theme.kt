package com.inovixlabs.tazkiyah.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The website's 4px corners, for anything drawn by hand (buttons, tiles, badges). */
val BrandShape = RoundedCornerShape(4.dp)

/** Square-cornered cards, sheets, dialogs, chips and fields, like the website. */
private val BrandShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = BrandShape,
    medium = BrandShape,
    large = BrandShape,
    extraLarge = BrandShape,
)

/** Brand colors always; dynamic (wallpaper) color is intentionally not used. */
@Composable
fun TazkiyahTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    font: AppFont = AppFont.NOTO_SANS,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = typographyFor(font),
        shapes = BrandShapes,
        content = content,
    )
}

@Composable
fun isAppInDarkTheme(): Boolean = MaterialTheme.colorScheme.background == DarkColors.background
