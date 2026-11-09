package com.inovixlabs.tazkiyah.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Charcoal and signal yellow — the same palette as the website (docs/assets/app.css) and the
// তারা-প্রদীপ logo. See docs/PLAN.md, "কালার থিম ও টাইপোগ্রাফি".

/** Page background in dark mode, the logo's ink, text on yellow. */
val Charcoal = Color(0xFF0A0A0A)

/** Top bars (the website's header), in both themes. */
val HeaderInk = Color(0xFF111111)

/** The brand accent: play buttons, the logo tile, selected tabs. Never text on a light background. */
val SignalYellow = Color(0xFFFACC15)

/** Raised panels on charcoal (the website's cards in dark mode). */
val Panel = Color(0xFF18181B)

/** Secondary text and icons on charcoal (7:1 on [HeaderInk]). */
val MutedOnDark = Color(0xFFA1A1AA)

val LightColors = lightColorScheme(
    primary = Charcoal,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = SignalYellow,
    onPrimaryContainer = Charcoal,
    // Ochre: the yellow family dark enough for text and thin lines on white (5.5:1).
    secondary = Color(0xFF8A6200),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF3D3300),
    tertiary = Color(0xFF3F3F46),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDEDE8),
    onTertiaryContainer = Charcoal,
    background = Color(0xFFF4F4F0),
    onBackground = Charcoal,
    surface = Color(0xFFFFFFFF),
    onSurface = Charcoal,
    surfaceVariant = Color(0xFFEDEDE8),
    onSurfaceVariant = Color(0xFF52525B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEDE8),
    surfaceContainerHighest = Color(0xFFE4E4DE),
    outline = Color(0xFF71717A),
    outlineVariant = Color(0xFFDEDED7),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

val DarkColors = darkColorScheme(
    primary = SignalYellow,
    onPrimary = Charcoal,
    primaryContainer = SignalYellow,
    onPrimaryContainer = Charcoal,
    secondary = SignalYellow,
    onSecondary = Charcoal,
    secondaryContainer = Color(0xFF2B2611),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = Color(0xFFD4D4D8),
    onTertiary = Color(0xFF18181B),
    tertiaryContainer = Color(0xFF27272A),
    onTertiaryContainer = Color(0xFFF4F4F5),
    background = Charcoal,
    onBackground = Color(0xFFF4F4F5),
    surface = HeaderInk,
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF27272A),
    onSurfaceVariant = MutedOnDark,
    surfaceContainerLowest = Charcoal,
    surfaceContainerLow = HeaderInk,
    surfaceContainer = Panel,
    surfaceContainerHigh = Color(0xFF1F1F23),
    surfaceContainerHighest = Color(0xFF27272A),
    outline = Color(0xFF71717A),
    outlineVariant = Color(0xFF333333),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)
