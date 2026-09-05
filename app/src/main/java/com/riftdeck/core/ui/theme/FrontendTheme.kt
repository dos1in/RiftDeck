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
            else listOf(0xFF646800, 0xFF006A73, 0xFFA52C68)
        ThemePalette.Ocean -> if (isDark) listOf(0xFF65C7FF, 0xFFB2A1FF, 0xFFFF94C7)
            else listOf(0xFF005DC3, 0xFF5261A5, 0xFF9F326A)
        ThemePalette.Ember -> if (isDark) listOf(0xFFFFB064, 0xFFD1A0FF, 0xFFFF8599)
            else listOf(0xFFAA4300, 0xFF79529E, 0xFF9D384D)
    }.map { Color(it) }
    return FrontendTheme(
        isDark = isDark,
        background = Color(if (isDark) 0xFF000000 else 0xFFF4F6F5),
        surface = Color(if (isDark) 0xFF090D0F else 0xFFFFFFFF),
        surfaceElevated = Color(if (isDark) 0xFF142023 else 0xFFE5EBE9),
        primary = accents[0],
        secondary = accents[1],
        tertiary = accents[2],
        onAccent = Color(if (isDark) 0xFF000000 else 0xFFFFFFFF),
        textPrimary = Color(if (isDark) 0xFFEEF5F3 else 0xFF152326),
        textSecondary = Color(if (isDark) 0xFFA5B6B8 else 0xFF526467),
        focusBorder = accents[0],
        outline = Color(if (isDark) 0xFF34484B else 0xFF879995),
        error = Color(if (isDark) 0xFFFF746C else 0xFFB32D25),
        success = Color(if (isDark) 0xFF70E2A0 else 0xFF196B40),
    )
}
