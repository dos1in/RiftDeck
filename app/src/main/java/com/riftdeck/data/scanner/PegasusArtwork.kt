package com.riftdeck.data.scanner

/** Reads artwork data only. Launch commands and all other metadata are ignored. */
internal object PegasusArtwork {
    fun paths(text: String, preferExplicit: Boolean = true, video: Boolean = false): Map<String, String> = buildMap {
        var game: String? = null
        var field = ""
        val files = mutableListOf<String>()
        var cover: String? = null
        fun flush() {
            val path = cover.takeIf { preferExplicit } ?: game?.let { "media/$it/${if (video) "video.mp4" else "boxfront"}" }
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
                "assets.video", "assets.box_front" -> if ((key == "assets.video") == video && game != null && value.isNotBlank()) cover = value
            }
        }
        flush()
    }

    fun descriptions(text: String): Map<String, String> = buildMap {
        val files = mutableListOf<String>()
        val description = StringBuilder()
        var field = ""
        fun flush() {
            val value = description.toString().replace("\\n", "\n").trim().take(24000)
            if (value.isNotEmpty()) files.filter(::safePath).forEach { put(CoverFiles.key(it), value) }
            files.clear(); description.clear()
        }
        for (line in text.lineSequence()) {
            if (line.isBlank() || line.trimStart().startsWith('#')) continue
            val continued = line.first().isWhitespace()
            if (!continued) field = line.substringBefore(':').trim().lowercase()
            val value = if (continued) line.trim() else line.substringAfter(':', "").trim()
            when (field) {
                "game", "collection" -> flush()
                "file", "files" -> if (value.isNotBlank()) files.add(value)
                "description" -> { if (description.isNotEmpty()) description.append('\n'); description.append(value) }
            }
        }
        flush()
    }

    private fun safePath(path: String) = path.isNotBlank() && ':' !in path && '\\' !in path && '\u0000' !in path &&
        path.split('/').none { it.isEmpty() || it == "." || it == ".." }
}
