package com.riftdeck.data.scanner

import org.junit.Assert.*
import org.junit.Test

class PegasusArtworkTest {
    @Test fun mapsMultipleVersionsAndExplicitArtwork() {
        val paths = PegasusArtwork.paths("game: 中文游戏\nfiles:\n  游戏 [1].zip\n  游戏 [2].gba\nassets.box_front: media/共享/boxFront.png\nlaunch: ignored\ngame: Other\nfile: other.zip")
        assertEquals("media/共享/boxFront.png", paths["游戏 [1]"])
        assertEquals(paths["游戏 [1]"], paths["游戏 [2]"])
        assertEquals("media/Other/boxfront", paths["other"])
    }
    @Test fun keepsSubdirectoryVersionsDistinct() {
        val paths = PegasusArtwork.paths("game: 第一卷\nfile: 第一卷/game.zip\ngame: 第二卷\nfile: 第二卷/game.zip")
        assertEquals("media/第一卷/boxfront", paths["第一卷/game"])
        assertEquals("media/第二卷/boxfront", paths["第二卷/game"])
    }
    @Test fun rejectsPathsOutsideLibraryAndDoesNotReadCommands() {
        assertTrue(PegasusArtwork.paths("game: A\nfile: a.zip\nassets.box_front: ../secret.png").isEmpty())
        assertTrue(PegasusArtwork.paths("launch: command\n  file: evil.zip").isEmpty())
        assertTrue(PegasusArtwork.paths("game: A\nfile: a.zip\nassets.box_front: https://example.com/image.png").isEmpty())
    }
}
