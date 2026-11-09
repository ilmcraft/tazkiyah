package com.inovixlabs.tazkiyah.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.inovixlabs.tazkiyah.R

/**
 * The reading font, chosen in Settings. All are bundled (SIL OFL, subset to Bengali + Latin) so
 * text looks the same offline and on every phone; [SYSTEM] uses the phone's own Bengali font.
 */
enum class AppFont(val label: Int) {
    NOTO_SANS(R.string.font_noto_sans),
    HIND_SILIGURI(R.string.font_hind_siliguri),
    ANEK_BANGLA(R.string.font_anek_bangla),
    BALOO_DA(R.string.font_baloo_da),
    NOTO_SERIF(R.string.font_noto_serif),
    SYSTEM(R.string.font_system),
}

private val UI_WEIGHTS = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/** A variable font (one file, a wght axis) exposed as the four weights the UI uses. */
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int, minWeight: Int = 100) = FontFamily(
    UI_WEIGHTS.map { weight ->
        Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(maxOf(weight.weight, minWeight))))
    },
)

private val HindSiliguri = FontFamily(
    Font(R.font.hind_siliguri_regular, FontWeight.Normal),
    Font(R.font.hind_siliguri_medium, FontWeight.Medium),
    Font(R.font.hind_siliguri_semibold, FontWeight.SemiBold),
    Font(R.font.hind_siliguri_semibold, FontWeight.Bold),
)
private val NotoSansBengali = variableFamily(R.font.noto_sans_bengali)
private val NotoSerifBengali = variableFamily(R.font.noto_serif_bengali)
private val AnekBangla = variableFamily(R.font.anek_bangla)
private val BalooDa = variableFamily(R.font.baloo_da_2, minWeight = 400)

fun AppFont.family(): FontFamily = when (this) {
    AppFont.NOTO_SANS -> NotoSansBengali
    AppFont.HIND_SILIGURI -> HindSiliguri
    AppFont.ANEK_BANGLA -> AnekBangla
    AppFont.BALOO_DA -> BalooDa
    AppFont.NOTO_SERIF -> NotoSerifBengali
    AppFont.SYSTEM -> FontFamily.Default
}

/**
 * The brand's heading face, as on the website: Anek Bangla at its narrowest width (wdth 75) and
 * ExtraBold, a static instance subset to Bengali (Latin falls back to the system font). Headings,
 * screen titles and section titles use it whatever reading font is chosen in Settings.
 */
val BrandHeading = FontFamily(Font(R.font.anek_bangla_condensed_extrabold, FontWeight.ExtraBold))

// Bengali matras rise and fall, so line height is ~1.45x the font size.
private fun style(family: FontFamily, weight: FontWeight, size: Int, lineHeight: TextUnit = (size * 1.45f).sp) =
    TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight)

private fun heading(size: Int) = style(BrandHeading, FontWeight.ExtraBold, size, (size * 1.3f).sp)

private fun typographyFor(f: FontFamily) = Typography(
    displayLarge = heading(54),
    displayMedium = heading(44),
    displaySmall = heading(36),
    headlineLarge = heading(32),
    headlineMedium = heading(28),
    headlineSmall = style(f, FontWeight.SemiBold, 23),
    titleLarge = heading(23),
    titleMedium = style(f, FontWeight.SemiBold, 17),
    titleSmall = style(f, FontWeight.Medium, 15),
    bodyLarge = style(f, FontWeight.Normal, 16),
    bodyMedium = style(f, FontWeight.Normal, 14),
    bodySmall = style(f, FontWeight.Normal, 12),
    labelLarge = style(f, FontWeight.Medium, 14),
    labelMedium = style(f, FontWeight.Medium, 12),
    labelSmall = style(f, FontWeight.Medium, 11),
)

private val typographies = AppFont.entries.associateWith { typographyFor(it.family()) }

fun typographyFor(font: AppFont): Typography = typographies.getValue(font)
