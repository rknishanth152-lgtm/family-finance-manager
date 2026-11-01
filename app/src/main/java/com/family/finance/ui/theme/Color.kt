package com.family.finance.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Full Material 3 role set, calm blue primary for a finance app.
 * Income uses the tertiary (green-teal) roles, expense uses error roles; both are always
 * paired with an icon and a +/- sign so colour is never the only cue (PRD 14.4).
 */

val LightColors = lightColorScheme(
    primary = Color(0xFF00658E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    inversePrimary = Color(0xFF84CFFF),
    secondary = Color(0xFF4F616E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E5F5),
    onSecondaryContainer = Color(0xFF0B1D29),
    tertiary = Color(0xFF006B5B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9EF2DC),
    onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceTint = Color(0xFF00658E),
    inverseSurface = Color(0xFF2D3135),
    inverseOnSurface = Color(0xFFEEF1F5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7F9FC),
    surfaceDim = Color(0xFFD7DADD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F7),
    surfaceContainer = Color(0xFFEBEEF1),
    surfaceContainerHigh = Color(0xFFE6E8EB),
    surfaceContainerHighest = Color(0xFFE0E3E6),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF84CFFF),
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF004C6C),
    onPrimaryContainer = Color(0xFFC7E7FF),
    inversePrimary = Color(0xFF00658E),
    secondary = Color(0xFFB6C9D8),
    onSecondary = Color(0xFF21323E),
    secondaryContainer = Color(0xFF374955),
    onSecondaryContainer = Color(0xFFD2E5F5),
    tertiary = Color(0xFF82D5C0),
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF005144),
    onTertiaryContainer = Color(0xFF9EF2DC),
    background = Color(0xFF101417),
    onBackground = Color(0xFFE0E3E6),
    surface = Color(0xFF101417),
    onSurface = Color(0xFFE0E3E6),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceTint = Color(0xFF84CFFF),
    inverseSurface = Color(0xFFE0E3E6),
    inverseOnSurface = Color(0xFF2D3135),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41484D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF363A3E),
    surfaceDim = Color(0xFF101417),
    surfaceContainerLowest = Color(0xFF0B0F12),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF272A2E),
    surfaceContainerHighest = Color(0xFF313539),
)
