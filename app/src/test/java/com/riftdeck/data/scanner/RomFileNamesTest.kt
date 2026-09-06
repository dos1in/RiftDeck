package com.riftdeck.data.scanner

import org.junit.Assert.*
import org.junit.Test

class RomFileNamesTest {
    @Test fun detectsOnlySupportedFinalExtensions() {
        assertEquals(1L, RomFileNames.platform("My Game.GBA"))
        assertEquals(1L, RomFileNames.platform("game.ZiP"))
        assertNull(RomFileNames.platform("game.gba.jpg"))
        assertNull(RomFileNames.platform("gba"))
        assertNull(RomFileNames.platform("game.7z"))
    }

    @Test fun keepsReleaseTagsAndDotsWhileCleaningFilenameSpacing() {
        assertEquals("Dr. Mario (USA) [Rev 1]", RomFileNames.title("Dr._Mario (USA) [Rev 1].gba"))
        assertEquals("Game Name", RomFileNames.title("Game__Name.gba"))
    }

    @Test fun normalizesSortWithoutChangingDisplayTitle() {
        assertEquals("pokemon", RomFileNames.sortTitle("Pokémon"))
        assertEquals("abc", RomFileNames.sortTitle("ＡＢＣ"))
        assertEquals("口袋妖怪", RomFileNames.sortTitle("口袋妖怪"))
    }
}
