package com.riftdeck.data.scanner

import java.text.Normalizer
import java.util.Locale

/** Platform IDs are data identifiers; UI and repositories do not inspect extensions. */
object RomFileNames {
    private val extensions = mapOf("gba" to 1L, "zip" to 1L)
    fun platform(name: String): Long? = extensions[name.substringAfterLast('.', "").lowercase(Locale.ROOT)]
    fun title(name: String): String = name.substringBeforeLast('.', name)
        .replace('_', ' ').replace(Regex("\\s+"), " ").trim().ifEmpty { name }
    fun sortTitle(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
}

data class RomDocument(
    val identity: String, val uri: String, val name: String, val size: Long, val modifiedAt: Long,
    val platformId: Long,
    val title: String = RomFileNames.title(name),
    val sortTitle: String = RomFileNames.sortTitle(title),
    val coverUri: String? = null,
    val coverVersion: String? = null,
)

interface RomDocumentSource {
    suspend fun enumerate(folder: String, onDocument: suspend (RomDocument) -> Unit)
}
