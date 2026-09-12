package com.riftdeck.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class FrontendTheme(
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

internal fun frontendTheme(): FrontendTheme = FrontendTheme(
    background = Color.Black,
    surface = Color(0xFF090D0F),
    surfaceElevated = Color(0xFF142023),
    primary = Color(0xFFF8F800),
    secondary = Color(0xFF00F0F0),
    tertiary = Color(0xFFFF4FA3),
    onPrimary = Color.Black,
    accentText = Color(0xFFF8F800),
    onAccent = Color.Black,
    textPrimary = Color(0xFFEEF5F3),
    textSecondary = Color(0xFFA5B6B8),
    focusBorder = Color(0xFFF8F800),
    outline = Color(0xFF34484B),
    error = Color(0xFFFF746C),
    success = Color(0xFF70E2A0),
)
