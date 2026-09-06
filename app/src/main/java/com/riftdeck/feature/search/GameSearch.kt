package com.riftdeck.feature.search

import com.riftdeck.core.model.Game
import java.text.Normalizer
import java.util.Locale

private val marks = Regex("\\p{M}+")
private val spaces = Regex("\\s+")

fun normalizeSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(marks, "").lowercase(Locale.ROOT)

fun searchGames(games: List<Game>, query: String): List<Game> {
    val terms = normalizeSearch(query.trim()).split(spaces).filter(String::isNotEmpty)
    if (terms.isEmpty()) return games
    return games.filter { game ->
        val text = normalizeSearch("${game.title} ${game.fileName}")
        terms.all(text::contains)
    }
}
