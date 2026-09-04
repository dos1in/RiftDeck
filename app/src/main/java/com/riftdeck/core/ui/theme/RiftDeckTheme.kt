/* Hallmark · pre-emit critique: P5 H5 E4 S5 R5 V5 · macrostructure: Workbench · tone: technical atmospheric · anchor hue: neon yellow · theme: Terminal */
package com.riftdeck.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class FrontendTheme(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val focusBorder: Color,
    val outline: Color,
    val error: Color,
    val success: Color,
)

private val NeonFrontendTheme = FrontendTheme(
    background = Color(0xFF10120E),
    surface = Color(0xFF171A15),
    surfaceElevated = Color(0xFF20241D),
    primary = Color(0xFFE7FF4F),
    secondary = Color(0xFF54DDEA),
    tertiary = Color(0xFFFF4FA3),
    textPrimary = Color(0xFFF1F3EB),
    textSecondary = Color(0xFFB7BEAE),
    focusBorder = Color(0xFFE7FF4F),
    outline = Color(0xFF46503F),
    error = Color(0xFFFF746C),
    success = Color(0xFF70E2A0),
)

val LocalFrontendTheme = staticCompositionLocalOf { NeonFrontendTheme }

private val RiftDeckTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 25.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun RiftDeckTheme(content: @Composable () -> Unit) {
    val colors = NeonFrontendTheme
    val materialColors = darkColorScheme(
        primary = colors.primary,
        onPrimary = colors.background,
        secondary = colors.secondary,
        onSecondary = colors.background,
        tertiary = colors.tertiary,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        error = colors.error,
    )

    androidx.compose.runtime.CompositionLocalProvider(LocalFrontendTheme provides colors) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = RiftDeckTypography,
            content = content,
        )
    }
}
