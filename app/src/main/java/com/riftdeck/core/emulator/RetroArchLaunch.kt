package com.riftdeck.core.emulator

/** RetroArch's native entry point, matching the Android frontend launch contract. */
internal object RetroArchLaunch {
    const val packageName = "com.retroarch.aarch64"
    const val activityName = "com.retroarch.browser.retroactivity.RetroActivityFuture"

    fun supports(config: EmulatorConfig) = config.packageName == packageName &&
        config.activityName == activityName && config.platformId == 1L

    fun extras(romPath: String, externalRoot: String): Map<String, String> = mapOf(
        "ROM" to romPath,
        "LIBRETRO" to "/data/data/$packageName/cores/mgba_libretro_android.so",
        "CONFIGFILE" to "$externalRoot/Android/data/$packageName/files/retroarch.cfg",
    )

    // Only Android's external-storage provider has this documented document ID layout.
    // Never interpret arbitrary provider IDs as filesystem paths.
    fun documentPath(documentId: String, primaryRoot: String): String? {
        val volume = documentId.substringBefore(':', "")
        val relative = documentId.substringAfter(':', "")
        if (relative.isBlank() || relative.split('/').any { it.isEmpty() || it == "." || it == ".." } ||
            relative.any { it == '\u0000' || it == '\\' }) return null
        val root = when {
            volume == "primary" -> primaryRoot
            volume.matches(Regex("[0-9a-fA-F]{4}-[0-9a-fA-F]{4}")) -> "/storage/$volume"
            else -> return null
        }
        return "$root/$relative"
    }
}
