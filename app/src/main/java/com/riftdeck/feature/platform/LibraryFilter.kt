package com.riftdeck.feature.platform

import com.riftdeck.core.model.Game
import com.riftdeck.feature.search.searchGames
import java.util.Locale

enum class LibraryFilter { All, Favorites, Recent }

fun libraryGames(games: List<Game>, filter: LibraryFilter, descending: Boolean, query: String = ""): List<Game> {
    val visible = searchGames(games, query).filter { game ->
        !game.hidden && when (filter) {
            LibraryFilter.All -> true
            LibraryFilter.Favorites -> game.favorite
            LibraryFilter.Recent -> game.lastPlayedAt != null
        }
    }
    if (filter == LibraryFilter.Recent) {
        return visible.sortedWith(compareByDescending<Game> { it.lastPlayedAt }.thenBy { it.id })
    }
    val ordered = visible.sortedWith(compareBy<Game> { it.sortTitle.lowercase(Locale.ROOT) }.thenBy { it.id })
    return if (descending) ordered.reversed() else ordered
}
