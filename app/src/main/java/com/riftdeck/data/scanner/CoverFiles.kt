package com.riftdeck.data.scanner

import java.text.Normalizer
import java.util.Locale

data class CoverFile(val name: String, val uri: String, val size: Long, val modifiedAt: Long) {
    val version: String get() = "$modifiedAt:$size"
}

/** Sidecar artwork stays inside the already authorized ROM folder. */
object CoverFiles {
    private val extensions = listOf("png", "webp", "jpg", "jpeg")
    fun supported(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions
    fun key(name: String): String = Normalizer.normalize(name.substringBeforeLast('.', name), Normalizer.Form.NFC)
        .lowercase(Locale.ROOT)
    fun index(files: List<CoverFile>): Map<String, CoverFile> = files.filter { supported(it.name) }
        .sortedWith(compareBy<CoverFile> { extensions.indexOf(it.name.substringAfterLast('.').lowercase(Locale.ROOT)) }
            .thenBy { it.name })
        .groupBy { key(it.name) }.mapValues { (_, candidates) -> candidates.first() }
}
