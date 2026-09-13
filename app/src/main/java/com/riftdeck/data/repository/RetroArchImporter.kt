package com.riftdeck.data.repository

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract as Docs
import androidx.room.withTransaction
import com.riftdeck.data.database.LibraryDatabase
import com.riftdeck.data.scanner.RetroArchPlaylist
import com.riftdeck.data.scanner.RomFileNames
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Imports metadata only. Playlist core commands and arbitrary filesystem paths are never executed. */
class RetroArchImporter(private val resolver: ContentResolver, private val database: LibraryDatabase) {
    data class Result(val matched: Int, val skipped: Int)
    private data class Entry(val uri: Uri, val path: String, val size: Long, val modified: Long)

    suspend fun import(tree: Uri): Result = withContext(Dispatchers.IO) {
        require(Docs.isTreeUri(tree))
        resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val files = mutableListOf<Entry>()
        val pending = ArrayDeque<Pair<String, String>>()
        val seen = mutableSetOf<String>()
        pending.add(Docs.getTreeDocumentId(tree) to "")
        var count = 0
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (id, prefix) = pending.removeFirst()
            if (!seen.add(id)) continue
            val children = Docs.buildChildDocumentsUriUsingTree(tree, id)
            resolver.query(children, arrayOf(Docs.Document.COLUMN_DOCUMENT_ID, Docs.Document.COLUMN_DISPLAY_NAME,
                Docs.Document.COLUMN_MIME_TYPE, Docs.Document.COLUMN_SIZE, Docs.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { cursor ->
                if (cursor.extras.getBoolean(Docs.EXTRA_LOADING)) throw IOException("Provider loading")
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    require(++count <= 100000) { "Directory too large" }
                    val child = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val path = prefix + name
                    if (cursor.getString(2) == Docs.Document.MIME_TYPE_DIR) {
                        // Only inspect exported playlist/thumbnail directories, never the ROM library.
                        if (prefix.isNotEmpty() || name == "playlists" || name == "thumbnails") {
                            require(path.count { it == '/' } < 8)
                            pending.add(child to "$path/")
                        }
                    } else if (name.endsWith(".lpl", true) || name.endsWith(".png", true)) {
                        files.add(Entry(Docs.buildDocumentUriUsingTree(tree, child), path, cursor.getLong(3), cursor.getLong(4)))
                    }
                }
            } ?: throw IOException("Directory unavailable")
        }
        val playlists = files.filter { it.path.endsWith(".lpl", true) }
        require(playlists.isNotEmpty()) { "No playlists" }
        val games = database.games().observeGames().first().filter { it.platformId == 1L }
        val byName = games.groupBy { it.fileName }
        val byUri = games.associateBy { it.romUri }
        val images = files.associateBy { it.path }
        data class Update(val id: Long, val title: String, val cover: Entry?)
        val updates = linkedMapOf<Long, Update>()
        var skipped = 0
        for (playlist in playlists.sortedBy { it.path }) {
            currentCoroutineContext().ensureActive()
            require(playlist.size <= 4 * 1024 * 1024)
            val bytes = resolver.openInputStream(playlist.uri)?.use { it.readBytesBounded() } ?: throw IOException("Playlist unavailable")
            val entries = try { RetroArchPlaylist.parse(bytes.toString(Charsets.UTF_8), playlist.path.substringAfterLast('/')) }
                catch (error: org.json.JSONException) { throw IOException("Invalid playlist", error) }
            for (entry in entries) {
                currentCoroutineContext().ensureActive()
                val candidates = byName[RetroArchPlaylist.fileName(entry.path)].orEmpty()
                val exact = candidates.filter { game ->
                    game.romUri == entry.path || runCatching {
                        val uri = Uri.parse(game.romUri)
                        if (uri.authority != "com.android.externalstorage.documents") false
                        else com.riftdeck.core.emulator.RetroArchLaunch.documentPath(Docs.getDocumentId(uri),
                            android.os.Environment.getExternalStorageDirectory().absolutePath) == entry.path.substringBefore('#')
                    }.getOrDefault(false)
                }
                val game = byUri[entry.path] ?: exact.singleOrNull() ?: candidates.singleOrNull()
                if (game == null) { skipped++; continue }
                val db = entry.database.removeSuffix(".lpl")
                val coverName = RetroArchPlaylist.thumbnailName(entry.label)
                val cover = listOf("Named_Boxarts", "Named_Snaps", "Named_Titles").firstNotNullOfOrNull { type ->
                    images["thumbnails/$db/$type/$coverName"]
                }
                updates.putIfAbsent(game.id, Update(game.id, entry.label, cover))
            }
        }
        database.withTransaction {
            updates.values.forEach { update ->
                database.games().importRetroArchMetadata(update.id, update.title, RomFileNames.sortTitle(update.title),
                    update.cover?.uri?.toString(), update.cover?.let { "${it.size}:${it.modified}" })
            }
        }
        Result(updates.size, skipped)
    }

    private suspend fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= 4 * 1024 * 1024)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
