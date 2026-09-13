package com.riftdeck.core.emulator

import org.junit.Assert.*
import org.junit.Test

class GbaEmulatorProfilesTest {
    @Test fun otherPlatformsDoNotReceiveGbaProfiles() {
        assertTrue(GbaEmulatorProfiles.forPlatform(2).isEmpty())
    }

    @Test fun standaloneEmulatorsUseGameEntryAndContentView() {
        val profiles = GbaEmulatorProfiles.forPlatform(1)
        val myBoy = profiles.single { it.packageName == "com.fastemulator.gba" }
        assertEquals("com.fastemulator.gba.EmulatorActivity", myBoy.activityName)
        assertEquals("android.intent.action.VIEW", myBoy.action)
        assertFalse(RetroArchLaunch.supports(myBoy))
        assertEquals("com.sky.SkyEmu.EnhancedNativeActivity",
            profiles.single { it.packageName == "com.sky.SkyEmu" }.activityName)
    }

    @Test fun eachRetroArchVariantUsesItsOwnPrivateCoreAndConfiguration() {
        RetroArchLaunch.packageNames.forEach { pkg ->
            val profile = GbaEmulatorProfiles.forPlatform(1).single { it.packageName == pkg }
            assertTrue(RetroArchLaunch.supports(profile))
            val extras = RetroArchLaunch.extras("/storage/AB12-CD34/Roms/Game.zip", "/storage/emulated/0", pkg)
            assertEquals("/data/data/$pkg/cores/mgba_libretro_android.so", extras["LIBRETRO"])
            assertEquals("/storage/emulated/0/Android/data/$pkg/files/retroarch.cfg", extras["CONFIGFILE"])
        }
    }
}
