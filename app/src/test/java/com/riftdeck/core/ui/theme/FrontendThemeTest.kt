package com.riftdeck.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.riftdeck.core.model.ThemeMode
import com.riftdeck.core.model.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontendThemeTest {
    @Test fun onlySystemModeTracksDeviceAppearance() {
        for (systemDark in listOf(false, true)) {
            assertEquals(systemDark, ThemeMode.System.isDark(systemDark))
            assertEquals(false, ThemeMode.Light.isDark(systemDark))
            assertEquals(true, ThemeMode.Dark.isDark(systemDark))
        }
    }

    @Test fun everyPaletteHasReadableTextAndVisibleFocusInBothModes() {
        for (dark in listOf(false, true)) for (palette in ThemePalette.entries) {
            val theme = frontendTheme(dark, palette)
            val surfaces = listOf(theme.background, theme.surface, theme.surfaceElevated)
            for (surface in surfaces) {
                for (text in listOf(theme.textPrimary, theme.textSecondary, theme.accentText, theme.secondary, theme.tertiary)) {
                    assertTrue("$palette dark=$dark text contrast=${contrast(text, surface)}", contrast(text, surface) >= 4.5)
                }
                assertTrue("$palette dark=$dark focus", contrast(theme.focusBorder, surface) >= 3.0)
            }
            assertTrue("$palette dark=$dark primary control", contrast(theme.onPrimary, theme.primary) >= 4.5)
            for (fill in listOf(theme.secondary, theme.tertiary, theme.error)) {
                assertTrue("$palette dark=$dark filled control", contrast(theme.onAccent, fill) >= 4.5)
            }
        }
    }

    private fun contrast(first: Color, second: Color): Double {
        val a = first.luminance().toDouble()
        val b = second.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
