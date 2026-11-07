package com.inovixlabs.tazkiyah.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// "পান্না ও সোনালি" — exact values from docs/PLAN.md, "কালার থিম ও টাইপোগ্রাফি".
val LightColors = lightColorScheme(
    primary = Color(0xFF0F5C4D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBE1),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF8A6D1F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E3B5),
    onSecondaryContainer = Color(0xFF261A00),
    tertiary = Color(0xFF2C4A6E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF001C38),
    background = Color(0xFFFAF7F0),
    onBackground = Color(0xFF1B1C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFEEF1EC),
    onSurfaceVariant = Color(0xFF404944),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF4F2EC),
    surfaceContainerHighest = Color(0xFFEEF1EC),
    outline = Color(0xFF6F7A75),
    outlineVariant = Color(0xFFD5DBD7),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD6BE),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF0F5C4D),
    onPrimaryContainer = Color(0xFFCDEBE1),
    secondary = Color(0xFFE3C46B),
    onSecondary = Color(0xFF3D2E00),
    secondaryContainer = Color(0xFF4A3B0A),
    onSecondaryContainer = Color(0xFFF3E3B5),
    tertiary = Color(0xFFA9C7EE),
    onTertiary = Color(0xFF0F3152),
    tertiaryContainer = Color(0xFF2C4A6E),
    onTertiaryContainer = Color(0xFFD3E4FF),
    background = Color(0xFF0F1714),
    onBackground = Color(0xFFE4E8E4),
    surface = Color(0xFF16201C),
    onSurface = Color(0xFFE4E8E4),
    surfaceVariant = Color(0xFF22302A),
    onSurfaceVariant = Color(0xFFBFC9C3),
    surfaceContainerLowest = Color(0xFF0B120F),
    surfaceContainerLow = Color(0xFF16201C),
    surfaceContainer = Color(0xFF16201C),
    surfaceContainerHigh = Color(0xFF1C2823),
    surfaceContainerHighest = Color(0xFF22302A),
    outline = Color(0xFF8A9590),
    outlineVariant = Color(0xFF3A4842),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** Bright gold — icons and lines only, never text (contrast). */
val BrightGold = Color(0xFFC9A227)
