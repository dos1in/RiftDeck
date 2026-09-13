package com.riftdeck.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class WordmarkTransparencyTest {
    @Test fun blackBackgroundIsTransparent() {
        assertEquals(0, transparentWordmarkPixel(0xff000000.toInt()))
        assertEquals(0, transparentWordmarkPixel(0xff050508.toInt()))
    }
    @Test fun brightBrandColorIsPreserved() {
        assertEquals(0xff00ffff.toInt(), transparentWordmarkPixel(0xff00ffff.toInt()))
    }
    @Test fun AntialiasedEdgeUsesAlphaInsteadOfBlack() {
        assertEquals(0x8000ffff.toInt(), transparentWordmarkPixel(0xff008080.toInt()))
    }
}
