package com.riftdeck.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import com.riftdeck.core.model.ThemeMode
import com.riftdeck.core.model.ThemePalette
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocalFrontendTheme = staticCompositionLocalOf { frontendTheme(isDark = true, ThemePalette.Rift) }
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
fun RiftDeckTheme(
    mode: ThemeMode = ThemeMode.System,
    palette: ThemePalette = ThemePalette.Rift,
    content: @Composable () -> Unit,
) {
    val isDark = mode.isDark(isSystemInDarkTheme())
    val colors = remember(isDark, palette) { frontendTheme(isDark, palette) }
    val brandArtwork = rememberBrandArtwork()
    val materialColors = remember(colors) {
        val base = if (isDark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = colors.primary,
            onPrimary = colors.onAccent,
            secondary = colors.secondary,
            onSecondary = colors.onAccent,
            tertiary = colors.tertiary,
            onTertiary = colors.onAccent,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.outline,
            surfaceTint = colors.primary,
            error = colors.error,
            onError = colors.onAccent,
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalFrontendTheme provides colors,
        LocalBrandArtwork provides brandArtwork,
    ) {
        MaterialTheme(colorScheme = materialColors, typography = RiftDeckTypography, content = content)
    }
}
