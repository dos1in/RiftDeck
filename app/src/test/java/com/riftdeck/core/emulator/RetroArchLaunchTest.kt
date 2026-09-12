package com.riftdeck.core.emulator

import org.junit.Assert.*
import org.junit.Test

class RetroArchLaunchTest {
    @Test fun preservesSdNamesAndArchivePath() {
        assertEquals("/storage/AB12-CD34/Roms/GBA/黄金太阳 1.zip",
            RetroArchLaunch.documentPath("AB12-CD34:Roms/GBA/黄金太阳 1.zip", "/storage/emulated/0"))
    }
    @Test fun resolvesPrimaryStorage() {
        assertEquals("/storage/emulated/0/Roms/game.gba",
            RetroArchLaunch.documentPath("primary:Roms/game.gba", "/storage/emulated/0"))
    }
    @Test fun rejectsUntrustedDocumentIds() {
        listOf("raw:/etc/file", "primary:../game.gba", "primary:/game.gba", "primary:",
            "AB12-CD34:Roms/./game.gba", "primary:Roms//game.gba", "primary:Roms/\u0000.gba")
            .forEach { assertNull(it, RetroArchLaunch.documentPath(it, "/storage/emulated/0")) }
    }
    @Test fun usesExistingRetroArchCoreAndConfig() {
        val extras = RetroArchLaunch.extras("/storage/AB12-CD34/Roms/game.zip", "/storage/emulated/0")
        assertEquals("/storage/AB12-CD34/Roms/game.zip", extras["ROM"])
        assertEquals("/data/data/com.retroarch.aarch64/cores/mgba_libretro_android.so", extras["LIBRETRO"])
        assertEquals("/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg", extras["CONFIGFILE"])
    }
    @Test fun onlyHandlesGbaRetroArchComponent() {
        val config = EmulatorConfig(1, RetroArchLaunch.packageName, RetroArchLaunch.activityName, "RetroArch G")
        assertTrue(RetroArchLaunch.supports(config))
        assertFalse(RetroArchLaunch.supports(config.copy(platformId = 2)))
        assertFalse(RetroArchLaunch.supports(config.copy(activityName = "Other")))
    }
}
