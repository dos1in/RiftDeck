package com.riftdeck.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.riftdeck.core.model.ThemePalette

@Immutable
data class FrontendTheme(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val onPrimary: Color,
    val accentText: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val focusBorder: Color,
    val outline: Color,
    val error: Color,
    val success: Color,
)

internal fun frontendTheme(isDark: Boolean, palette: ThemePalette): FrontendTheme {
    val accents = when (palette) {
        ThemePalette.Rift -> if (isDark) listOf(0xFFF8F800, 0xFF00F0F0, 0xFFFF4FA3)
            else listOf(0xFFF3DF16, 0xFF006C90, 0xFFB02675)
        ThemePalette.Ocean -> if (isDark) listOf(0xFF65C7FF, 0xFFB2A1FF, 0xFFFF94C7)
            else listOf(0xFF096DE0, 0xFF5C45BF, 0xFFBE226A)
        ThemePalette.Ember -> if (isDark) listOf(0xFFFFB064, 0xFFD1A0FF, 0xFFFF8599)
            else listOf(0xFFFF9A3D, 0xFF7043B7, 0xFFB63251)
    }.map { Color(it) }
    // Bright fills and readable text/focus colors have different contrast needs on white.
    val accentText = if (isDark) accents[0] else when (palette) {
        ThemePalette.Rift -> Color(0xFF006C90)
        ThemePalette.Ocean -> Color(0xFF0865D1)
        ThemePalette.Ember -> Color(0xFFA83F09)
    }
    return FrontendTheme(
        isDark = isDark,
        background = Color(if (isDark) 0xFF000000 else 0xFFFFFFFF),
        surface = Color(if (isDark) 0xFF090D0F else 0xFFF7F9FC),
        surfaceElevated = Color(if (isDark) 0xFF142023 else 0xFFEDF3FA),
        primary = accents[0],
        secondary = accents[1],
        tertiary = accents[2],
        onPrimary = if (isDark) Color.Black else if (palette == ThemePalette.Ocean) Color.White else Color(0xFF182438),
        accentText = accentText,
        onAccent = Color(if (isDark) 0xFF000000 else 0xFFFFFFFF),
        textPrimary = Color(if (isDark) 0xFFEEF5F3 else 0xFF182438),
        textSecondary = Color(if (isDark) 0xFFA5B6B8 else 0xFF556276),
        focusBorder = accentText,
        outline = Color(if (isDark) 0xFF34484B else 0xFFB8C3D1),
        error = Color(if (isDark) 0xFFFF746C else 0xFFB32D25),
        success = Color(if (isDark) 0xFF70E2A0 else 0xFF196B40),
    )
}
