package com.family.finance.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.family.finance.R

/**
 * Noto Sans Tamil (SIL OFL 1.1, see licenses/NotoSansTamil-OFL.txt).
 * Latin glyphs not in the font fall back to the system font.
 */
val NotoSansTamil = FontFamily(
    Font(R.font.noto_sans_tamil_regular, FontWeight.Normal),
    Font(R.font.noto_sans_tamil_medium, FontWeight.Medium),
    Font(R.font.noto_sans_tamil_bold, FontWeight.Bold),
)

private val Default = Typography()

/** Extra line height keeps Tamil vowel signs from clipping (PRD 7.3). */
private fun TextStyle.tamil() = copy(fontFamily = NotoSansTamil, lineHeight = 1.5.em)

val AppTypography = Typography(
    displayLarge = Default.displayLarge.tamil(),
    displayMedium = Default.displayMedium.tamil(),
    displaySmall = Default.displaySmall.tamil(),
    headlineLarge = Default.headlineLarge.tamil(),
    headlineMedium = Default.headlineMedium.tamil(),
    headlineSmall = Default.headlineSmall.tamil(),
    titleLarge = Default.titleLarge.tamil(),
    titleMedium = Default.titleMedium.tamil(),
    titleSmall = Default.titleSmall.tamil(),
    bodyLarge = Default.bodyLarge.tamil(),
    bodyMedium = Default.bodyMedium.tamil(),
    bodySmall = Default.bodySmall.tamil(),
    labelLarge = Default.labelLarge.tamil(),
    labelMedium = Default.labelMedium.tamil(),
    labelSmall = Default.labelSmall.tamil(),
)
