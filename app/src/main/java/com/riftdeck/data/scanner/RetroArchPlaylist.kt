package com.riftdeck.data.scanner

import org.json.JSONObject

internal data class PlaylistGame(val path: String, val label: String, val database: String)

internal object RetroArchPlaylist {
    fun parse(text: String, playlistName: String): List<PlaylistGame> {
        val clean = text.removePrefix("\uFEFF").trim()
        val entries = if (clean.startsWith("{")) {
            val items = JSONObject(clean).getJSONArray("items")
            require(items.length() <= 20000)
            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                PlaylistGame(item.optString("path"), item.optString("label"), item.optString("db_name", playlistName))
            }
        } else {
            val lines = clean.lines()
            require(lines.size % 6 == 0) { "Invalid legacy playlist" }
            lines.chunked(6).map { PlaylistGame(it[0], it[1], it[5].ifBlank { playlistName }) }
        }
        return entries.filter {
            val path = it.path.substringBefore('#')
            it.label.isNotBlank() && it.label.length <= 1024 &&
                (path.endsWith(".gba", true) || (path.endsWith(".zip", true) &&
                    (it.database.contains("Game Boy Advance", true) || it.path.substringAfter('#', "").endsWith(".gba", true))))
        }
    }

    fun fileName(path: String) = path.substringBefore('#').replace('\\', '/').substringAfterLast('/')
    fun thumbnailName(label: String) = label.map { if (it in "&*/:`<>?\\|\"") '_' else it }.joinToString("") + ".png"
}
