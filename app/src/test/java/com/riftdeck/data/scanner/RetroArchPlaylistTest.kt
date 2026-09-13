package com.riftdeck.data.scanner

import org.junit.Assert.*
import org.junit.Test

class RetroArchPlaylistTest {
    @Test fun legacyGbaPlaylistImportsLabelsWithoutCoreCommands() {
        val result = RetroArchPlaylist.parse("/roms/game.gba\n游戏标题\n/untrusted/core.so\nDETECT\nDETECT\nNintendo - Game Boy Advance.lpl", "gba.lpl")
        assertEquals(1, result.size)
        assertEquals("游戏标题", result.single().label)
    }
    @Test fun rejectsOtherSystemsAndAmbiguousZip() {
        assertTrue(RetroArchPlaylist.parse("/roms/game.zip\nGame\nDETECT\nDETECT\nDETECT\nNintendo - SNES.lpl", "list.lpl").isEmpty())
    }
    @Test fun archiveAndWindowsNamesArePortable() {
        assertEquals("Game.zip", RetroArchPlaylist.fileName("C:\\Roms\\Game.zip#Game.gba"))
        assertEquals("A_B_ C.png", RetroArchPlaylist.thumbnailName("A&B: C"))
    }
    @Test(expected = IllegalArgumentException::class) fun malformedLegacyIsRejected() {
        RetroArchPlaylist.parse("not a playlist", "list.lpl")
    }
}
