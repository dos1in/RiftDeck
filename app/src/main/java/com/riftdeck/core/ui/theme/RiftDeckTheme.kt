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
    background = Color(0xFF000000),
    surface = Color(0xFF090D0F),
    surfaceElevated = Color(0xFF142023),
    primary = Color(0xFFF8F800),
    secondary = Color(0xFF00F0F0),
    tertiary = Color(0xFFFF4FA3),
    textPrimary = Color(0xFFEEF5F3),
    textSecondary = Color(0xFFA5B6B8),
    focusBorder = Color(0xFFF8F800),
    outline = Color(0xFF34484B),
    error = Color(0xFFFF746C),
    success = Color(0xFF70E2A0),
)

val LocalFrontendTheme = staticCompositionLocalOf { NeonFrontendTheme }
val LocalReducedMotion = staticCompositionLocalOf { false }

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
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 19.sp,
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
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun RiftDeckTheme(content: @Composable () -> Unit) {
    val colors = NeonFrontendTheme
    val brandArtwork = rememberBrandArtwork()
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

    androidx.compose.runtime.CompositionLocalProvider(
        LocalFrontendTheme provides colors,
        LocalBrandArtwork provides brandArtwork,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = RiftDeckTypography,
            content = content,
        )
    }
}
