package com.riftdeck.data.scanner

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.riftdeck.data.scanner.RomDocument
import com.riftdeck.data.scanner.RomDocumentSource
import com.riftdeck.data.scanner.RomFileNames
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SafRomDocumentSource(private val resolver: ContentResolver) : RomDocumentSource {
    override suspend fun enumerate(folder: String, onDocument: suspend (RomDocument) -> Unit) = withContext(Dispatchers.IO) {
        val tree = Uri.parse(folder)
        val pending = ArrayDeque<String>()
        val seen = HashSet<String>()
        val mappedCovers = mutableMapOf<String, CoverFile>()
        val mappedVideos = mutableMapOf<String, String>()
        val descriptions = mutableMapOf<String, String>()
        pending.add(DocumentsContract.getTreeDocumentId(tree))
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val parent = pending.removeFirst()
            if (!seen.add(parent)) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
            val cursor = resolver.query(childrenUri, projection, null, null, null)
                ?: throw IOException("Document provider returned no cursor")
            cursor.use {
                if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING)) throw IOException("Document provider is still loading")
                val images = ArrayList<CoverFile>()
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val name = it.getString(1) ?: continue
                    if (it.getString(2) != Document.MIME_TYPE_DIR && CoverFiles.supported(name)) {
                        val id = it.getString(0) ?: throw IOException("Missing image document id")
                        images.add(CoverFile(name, DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                            it.getLong(3), it.getLong(4)))
                    }
                }
                val covers = CoverFiles.index(images).toMutableMap()
                it.moveToPosition(-1)
                val mediaIds = mutableListOf<String>()
                val romKeys = mutableSetOf<String>()
                var metadataId: String? = null
                while (it.moveToNext()) {
                    val name = it.getString(1) ?: continue
                    if (name == "metadata.pegasus.txt" && it.getLong(3) <= 4 * 1024 * 1024) metadataId = it.getString(0)
                    if (it.getString(2) != Document.MIME_TYPE_DIR && RomFileNames.platform(name) != null) romKeys.add(CoverFiles.key(name))
                    if (it.getString(2) == Document.MIME_TYPE_DIR && it.getString(1).equals("media", ignoreCase = true)) {
                        mediaIds.add(it.getString(0))
                    }
                }
                for (mediaId in mediaIds) {
                    for (gameDirectory in children(tree, mediaId).filter { entry -> entry.directory }) {
                        currentCoroutineContext().ensureActive()
                        val gameKey = CoverFiles.key(gameDirectory.name + ".gba")
                        if (gameKey !in romKeys || gameKey in covers) continue
                        val artwork = CoverFiles.pegasusCover(children(tree, gameDirectory.id).filterNot { entry -> entry.directory }
                            .map { entry -> CoverFile(entry.name, DocumentsContract.buildDocumentUriUsingTree(tree, entry.id).toString(), entry.size, entry.modified) })
                        if (artwork != null) covers.putIfAbsent(gameKey, artwork)
                    }
                }
                if (metadataId != null) {
                    val metadataUri = DocumentsContract.buildDocumentUriUsingTree(tree, metadataId)
                    val bytes = resolver.openInputStream(metadataUri)?.use { input -> java.io.ByteArrayOutputStream().also { output ->
                        val buffer = ByteArray(8192)
                        while (output.size() <= 4 * 1024 * 1024) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }.toByteArray() }
                    var metadataDescriptions = emptyMap<String, String>()
                    val paths = if (bytes != null && bytes.size <= 4 * 1024 * 1024) {
                        val text = bytes.toString(Charsets.UTF_8)
                        metadataDescriptions = PegasusArtwork.descriptions(text)
                        (PegasusArtwork.paths(text).toList() + PegasusArtwork.paths(text, preferExplicit = false).toList()).map { (key, path) -> Triple(key, path, false) } +
                            (PegasusArtwork.paths(text, video = true).toList() + PegasusArtwork.paths(text, preferExplicit = false, video = true).toList()).map { (key, path) -> Triple(key, path, true) }
                    } else emptyList()
                    val directoryCache = mutableMapOf<String, List<Entry>>()
                    for ((key, path, isVideo) in paths) {
                        val romSegments = key.split('/')
                        var romDirectory = parent
                        var romExists = true
                        for (segment in romSegments.dropLast(1)) {
                            val entries = directoryCache[romDirectory] ?: children(tree, romDirectory).also { result -> directoryCache[romDirectory] = result }
                            val next = entries.firstOrNull { entry -> entry.directory && CoverFiles.key(entry.name + ".gba") == segment }
                            if (next == null) { romExists = false; break }
                            romDirectory = next.id
                        }
                        if (!romExists) continue
                        val romEntries = directoryCache[romDirectory] ?: children(tree, romDirectory).also { result -> directoryCache[romDirectory] = result }
                        val rom = romEntries.firstOrNull { entry -> !entry.directory && RomFileNames.platform(entry.name) != null && CoverFiles.key(entry.name) == romSegments.last() } ?: continue
                        metadataDescriptions[key]?.let { value -> descriptions[rom.id] = value }
                        if (if (isVideo) rom.id in mappedVideos else rom.id in mappedCovers) continue
                        var directory = parent
                        val segments = path.split('/')
                        var valid = true
                        for (segment in segments.dropLast(1)) {
                            val entries = directoryCache[directory] ?: children(tree, directory).also { result -> directoryCache[directory] = result }
                            val next = entries.firstOrNull { entry -> entry.directory && entry.name == segment }
                            if (next == null) { valid = false; break }
                            directory = next.id
                        }
                        if (!valid) continue
                        val entries = directoryCache[directory] ?: children(tree, directory).also { result -> directoryCache[directory] = result }
                        val imagesInDirectory = entries.filterNot { entry -> entry.directory }.map { entry ->
                            CoverFile(entry.name, DocumentsContract.buildDocumentUriUsingTree(tree, entry.id).toString(), entry.size, entry.modified)
                        }
                        if (isVideo) {
                            imagesInDirectory.firstOrNull { image -> image.name.equals(segments.last(), ignoreCase = true) && image.name.endsWith(".mp4", ignoreCase = true) }
                                ?.let { video -> mappedVideos[rom.id] = video.uri }
                            continue
                        }
                        val artwork = if (segments.last() == "boxfront") CoverFiles.pegasusCover(imagesInDirectory)
                            else imagesInDirectory.firstOrNull { image -> image.name.equals(segments.last(), ignoreCase = true) && CoverFiles.supported(image.name) }
                                ?: CoverFiles.index(imagesInDirectory)[CoverFiles.key(segments.last())]
                        if (artwork != null) mappedCovers[rom.id] = artwork
                    }
                }
                it.moveToPosition(-1)
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val id = it.getString(0) ?: throw IOException("Missing document id")
                    val name = it.getString(1) ?: continue
                    if (it.getString(2) == Document.MIME_TYPE_DIR) {
                        if (id !in mediaIds) pending.add(id)
                    } else {
                        val platform = RomFileNames.platform(name) ?: continue
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        val cover = covers[CoverFiles.key(name)] ?: mappedCovers[id]
                        onDocument(RomDocument("${tree.authority}:$id", uri.toString(), name,
                            it.getLong(3), it.getLong(4), platform, coverUri = cover?.uri, coverVersion = cover?.version, description = descriptions[id], videoUri = mappedVideos[id]))
                    }
                }
            }
        }
    }
    private data class Entry(val id: String, val name: String, val directory: Boolean, val size: Long, val modified: Long)

    private suspend fun children(tree: Uri, parent: String): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
        return resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING)) throw IOException("Document provider is still loading")
            buildList {
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val id = cursor.getString(0) ?: throw IOException("Missing document id")
                    val name = cursor.getString(1) ?: continue
                    add(Entry(id, name, cursor.getString(2) == Document.MIME_TYPE_DIR, cursor.getLong(3), cursor.getLong(4)))
                }
            }
        } ?: throw IOException("Document provider returned no cursor")
    }

}
