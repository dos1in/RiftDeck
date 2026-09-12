package com.riftdeck.data.scanner

import org.junit.Assert.*
import org.junit.Test

class CoverFilesTest {
    @Test fun matchesCaseAndUnicodeWithoutRemovingReleaseTags() {
        val cover = CoverFile("POKÉMON (USA).PNG", "content://test/cover", 500, 1)
        val index = CoverFiles.index(listOf(cover))
        assertEquals(cover, index[CoverFiles.key("Poke\u0301mon (USA).gba")])
        assertNull(index[CoverFiles.key("Pokemon (Japan).gba")])
    }
    @Test fun choosesAStableFormatAndIgnoresUnsupportedFiles() {
        val jpg = CoverFile("Game.jpg", "jpg", 1, 1)
        val png = CoverFile("Game.png", "png", 1, 1)
        val gif = CoverFile("Other.gif", "gif", 1, 1)
        assertEquals(png, CoverFiles.index(listOf(jpg, gif, png))["game"])
        assertEquals(CoverFiles.index(listOf(jpg, png)), CoverFiles.index(listOf(png, jpg)))
        assertFalse(CoverFiles.index(listOf(gif)).containsKey("other"))
    }
    @Test fun pegasusPrefersFrontCoverOverThumbnailAndLogo() {
        val front = CoverFile("boxfront.jpg", "front", 100, 1)
        val mini = CoverFile("mini_boxfront.png", "mini", 10, 1)
        val logo = CoverFile("logo.png", "logo", 10, 1)
        assertEquals(front, CoverFiles.pegasusCover(listOf(logo, mini, front)))
        assertEquals(mini, CoverFiles.pegasusCover(listOf(logo, mini)))
        assertNull(CoverFiles.pegasusCover(listOf(logo)))
    }
    @Test fun cacheVersionTracksImageReplacement() {
        val image = CoverFile("Game.png", "content://test/cover", 500, 10)
        assertNotEquals(image.version, image.copy(size = 600).version)
        assertNotEquals(image.version, image.copy(modifiedAt = 11).version)
    }
}
