package com.riftdeck.data.repository

import com.riftdeck.core.model.Game
import com.riftdeck.core.model.RomCopy

/** Keep physical copies in Room for rescans and storage removal; expose one logical game. */
internal fun uniqueGames(games: List<Game>): List<Game> {
    val groups = games.filterNot { it.hidden }.groupBy { game ->
        game.sha1?.takeIf { it.isNotBlank() }?.let { "${game.platformId}:$it" } ?: "id:${game.id}"
    }
    return groups.values.map { copies ->
        val primary = copies.minBy { it.id }
        if (copies.size == 1) primary else {
            val artwork = copies.firstOrNull { it.id == primary.id && it.coverUri != null }
                ?: copies.firstOrNull { it.coverUri != null }
            primary.copy(
                favorite = copies.any { it.favorite },
                playCount = copies.sumOf { it.playCount },
                playTimeSeconds = copies.sumOf { it.playTimeSeconds },
                lastPlayedAt = copies.mapNotNull { it.lastPlayedAt }.maxOrNull(),
                coverUri = artwork?.coverUri,
                coverVersion = artwork?.coverVersion,
                screenshotUri = primary.screenshotUri ?: copies.firstNotNullOfOrNull { it.screenshotUri },
                videoUri = primary.videoUri ?: copies.firstNotNullOfOrNull { it.videoUri },
                description = primary.description ?: copies.firstNotNullOfOrNull { it.description },
                releaseYear = primary.releaseYear ?: copies.firstNotNullOfOrNull { it.releaseYear },
                developer = primary.developer ?: copies.firstNotNullOfOrNull { it.developer },
                genre = primary.genre ?: copies.firstNotNullOfOrNull { it.genre },
                sourceGameIds = copies.map { it.id },
                romCopies = copies.filterNot { it.id == primary.id }.map { RomCopy(it.romUri, it.fileName, it.fileSize) },
            )
        }
    }
}
