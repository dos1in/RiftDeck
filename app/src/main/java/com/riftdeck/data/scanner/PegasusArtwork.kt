package com.riftdeck.data.scanner

/** Reads artwork data only. Launch commands and all other metadata are ignored. */
internal object PegasusArtwork {
    fun paths(text: String, preferExplicit: Boolean = true): Map<String, String> = buildMap {
        var game: String? = null
        var field = ""
        val files = mutableListOf<String>()
        var cover: String? = null
        fun flush() {
            val path = cover.takeIf { preferExplicit } ?: game?.let { "media/$it/boxfront" }
            if (path != null && safePath(path)) files.filter { safePath(it) }
                .forEach { put(CoverFiles.key(it), path) }
            files.clear()
            cover = null
        }
        for (line in text.lineSequence()) {
            if (line.isBlank() || line.trimStart().startsWith('#')) continue
            val continuation = line.first().isWhitespace()
            val key = if (continuation) field else line.substringBefore(':').trim().lowercase()
            val value = if (continuation) line.trim() else line.substringAfter(':', "").trim()
            if (!continuation) field = key
            when (key) {
                "game" -> { flush(); game = value }
                "collection" -> { flush(); game = null }
                "file", "files" -> if (game != null && value.isNotBlank()) files.add(value)
                "assets.box_front" -> if (game != null && value.isNotBlank()) cover = value
            }
        }
        flush()
    }

    private fun safePath(path: String) = path.isNotBlank() && ':' !in path && '\\' !in path && '\u0000' !in path &&
        path.split('/').none { it.isEmpty() || it == "." || it == ".." }
}
