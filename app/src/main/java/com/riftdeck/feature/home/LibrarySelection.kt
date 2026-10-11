package com.riftdeck.feature.home

import com.riftdeck.core.model.Game

/** Keep the selected ROM (including merged IDs), or the nearest remaining shelf position. */
internal fun librarySelection(games: List<Game>, focusedId: Long?, previousIndex: Int): Game? =
    focusedId?.let { id -> games.firstOrNull { it.matchesId(id) } }
        ?: games.getOrNull(previousIndex.coerceIn(0, games.lastIndex.coerceAtLeast(0)))
