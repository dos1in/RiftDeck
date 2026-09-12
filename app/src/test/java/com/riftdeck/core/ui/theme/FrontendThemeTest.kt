package com.riftdeck.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontendThemeTest {
    @Test fun darkThemeHasReadableTextAndVisibleFocus() {
        val theme = frontendTheme()
        val surfaces = listOf(theme.background, theme.surface, theme.surfaceElevated)
        for (surface in surfaces) {
            for (text in listOf(theme.textPrimary, theme.textSecondary, theme.accentText, theme.secondary, theme.tertiary)) {
                assertTrue("Text contrast=${contrast(text, surface)}", contrast(text, surface) >= 4.5)
            }
            assertTrue("Focus contrast", contrast(theme.focusBorder, surface) >= 3.0)
        }
        assertTrue("Primary control contrast", contrast(theme.onPrimary, theme.primary) >= 4.5)
        for (fill in listOf(theme.secondary, theme.tertiary, theme.error)) {
            assertTrue("Filled control contrast", contrast(theme.onAccent, fill) >= 4.5)
        }
    }

    private fun contrast(first: Color, second: Color): Double {
        val a = first.luminance().toDouble()
        val b = second.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
