package com.inovixlabs.tazkiyah.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.inovixlabs.tazkiyah.R

/** Body and UI: Hind Siliguri (bundled TTF so it renders the same offline). */
val HindSiliguri = FontFamily(
    Font(R.font.hind_siliguri_regular, FontWeight.Normal),
    Font(R.font.hind_siliguri_medium, FontWeight.Medium),
    Font(R.font.hind_siliguri_semibold, FontWeight.SemiBold),
    Font(R.font.hind_siliguri_semibold, FontWeight.Bold),
)

/** Large headings only: Tiro Bangla gives the "kitabi" feel. */
val TiroBangla = FontFamily(Font(R.font.tiro_bangla_regular, FontWeight.Normal))

// Bengali matras rise and fall, so line height is ~1.45x the font size.
private fun style(family: FontFamily, weight: FontWeight, size: Int, lineHeight: TextUnit = (size * 1.45f).sp) =
    TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight)

val TazkiyahTypography = Typography(
    displayLarge = style(TiroBangla, FontWeight.Normal, 52),
    displayMedium = style(TiroBangla, FontWeight.Normal, 42),
    displaySmall = style(TiroBangla, FontWeight.Normal, 34),
    headlineLarge = style(TiroBangla, FontWeight.Normal, 30),
    headlineMedium = style(TiroBangla, FontWeight.Normal, 26),
    headlineSmall = style(TiroBangla, FontWeight.Normal, 23),
    titleLarge = style(HindSiliguri, FontWeight.SemiBold, 21),
    titleMedium = style(HindSiliguri, FontWeight.SemiBold, 17),
    titleSmall = style(HindSiliguri, FontWeight.Medium, 15),
    bodyLarge = style(HindSiliguri, FontWeight.Normal, 16),
    bodyMedium = style(HindSiliguri, FontWeight.Normal, 14),
    bodySmall = style(HindSiliguri, FontWeight.Normal, 12),
    labelLarge = style(HindSiliguri, FontWeight.Medium, 14),
    labelMedium = style(HindSiliguri, FontWeight.Medium, 12),
    labelSmall = style(HindSiliguri, FontWeight.Medium, 11),
)
