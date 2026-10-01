package com.riftdeck.core.emulator

import com.riftdeck.core.model.Game

/** A missing SD card must not prevent launching an identical copy in another location. */
suspend fun EmulatorLauncher.launchWithCopies(config: EmulatorConfig, game: Game): LaunchResult {
    fun LaunchResult.canTryCopy() = this == LaunchResult.RomUnavailable || this is LaunchResult.InvalidArchive
    val originalResult = launch(config, game)
    if (!originalResult.canTryCopy()) return originalResult
    for (copy in game.romCopies.distinctBy { it.uri }) {
        if (copy.uri == game.romUri) continue
        val result = launch(config, game.copy(romUri = copy.uri, fileName = copy.fileName,
            fileSize = copy.fileSize, romCopies = emptyList()))
        if (!result.canTryCopy()) return result
    }
    return originalResult
}
