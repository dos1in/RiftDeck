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
        pending.add(DocumentsContract.getTreeDocumentId(tree))
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val parent = pending.removeFirst()
            if (!seen.add(parent)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
            val cursor = resolver.query(children, projection, null, null, null)
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
                val covers = CoverFiles.index(images)
                it.moveToPosition(-1)
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val id = it.getString(0) ?: throw IOException("Missing document id")
                    val name = it.getString(1) ?: continue
                    if (it.getString(2) == Document.MIME_TYPE_DIR) {
                        pending.add(id)
                    } else {
                        val platform = RomFileNames.platform(name) ?: continue
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        val cover = covers[CoverFiles.key(name)]
                        onDocument(RomDocument("${tree.authority}:$id", uri.toString(), name,
                            it.getLong(3), it.getLong(4), platform, coverUri = cover?.uri, coverVersion = cover?.version))
                    }
                }
            }
        }
    }
}
