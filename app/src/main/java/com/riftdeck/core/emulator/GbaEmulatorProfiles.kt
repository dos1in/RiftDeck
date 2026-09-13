package com.riftdeck.core.emulator

/** Explicit game-entry components; generic ACTION_VIEW discovery remains the fallback. */
internal object GbaEmulatorProfiles {
    fun forPlatform(platformId: Long): List<EmulatorConfig> {
        if (platformId != 1L) return emptyList()
        return listOf(
            EmulatorConfig(platformId, "com.fastemulator.gba", "com.fastemulator.gba.EmulatorActivity", "My Boy!"),
            EmulatorConfig(platformId, "com.sky.SkyEmu", "com.sky.SkyEmu.EnhancedNativeActivity", "SkyEmu"),
            EmulatorConfig(platformId, "com.explusalpha.GbaEmu", "com.imagine.BaseActivity", "GBA.emu"),
        ) + RetroArchLaunch.packageNames.map { packageName ->
            val name = when (packageName) {
                "com.retroarch.aarch64" -> "RetroArch 64 / G"
                "com.retroarch.ra32" -> "RetroArch 32"
                else -> "RetroArch"
            }
            EmulatorConfig(platformId, packageName, RetroArchLaunch.activityName, "$name · mGBA")
        }
    }
}
