package com.riftdeck.data.sharing

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.AtomicFile
import com.riftdeck.core.sharing.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** All calls belong on Dispatchers.IO. Only explicitly granted document trees are reachable. */
class SafSharingStore(private val resolver: ContentResolver, private val cache: File, private val recoveryDir: File) {
    private val lock = ReentrantLock()
    private data class Entry(val id: String, val name: String, val directory: Boolean, val size: Long, val modified: Long)
    private data class CachedHash(val size: Long, val modified: Long, val hash: String)
    private val romHashes = mutableMapOf<String, CachedHash>()

    fun manifest(folder: String, kind: ShareKind): List<SharedFile> = lock.withLock {
        requireNoPendingRecovery(folder)
        val tree = tree(folder)
        val pending = ArrayDeque<Pair<String, String>>()
        val seen = mutableSetOf<String>()
        val result = mutableListOf<SharedFile>()
        pending.add(DocumentsContract.getTreeDocumentId(tree) to "")
        while (pending.isNotEmpty()) {
            if (Thread.currentThread().isInterrupted) throw IOException("Transfer stopped")
            val (parent, prefix) = pending.removeFirst()
            if (!seen.add(parent)) continue
            for (entry in children(tree, parent)) {
                val path = prefix + entry.name
                if (!isSafeSharingPath(path)) continue
                if (entry.directory) {
                    if (path.split('/').size < 16 && !entry.name.startsWith('.')) pending.add(entry.id to "$path/")
                } else if (supported(kind, path) && entry.size in 0..maxSize(kind)) {
                    // Never publish a save the emulator has just written. Timestamps are local only.
                    if (kind == ShareKind.Save && entry.modified > 0 && System.currentTimeMillis() - entry.modified in 0..1999) continue
                    // A ROM catalog must not read an entire SD card. Only selected downloads are hashed.
                    val descriptor = if (kind == ShareKind.Rom) SharedFile(path, entry.size, "0".repeat(64)) else describeEntry(tree, kind, path, entry)
                    result.add(descriptor)
                    if (result.size > 10000) throw IOException("Folder has too many files")
                }
            }
        }
        result.sortedBy { it.path }
    }

    fun describe(folder: String, kind: ShareKind, path: String): SharedFile = lock.withLock {
        requireSupported(kind, path)
        requireNoPendingRecovery(folder)
        val tree = tree(folder)
        val entry = find(tree, path) ?: throw IOException("File unavailable")
        describeEntry(tree, kind, path, entry)
    }

    private fun describeEntry(tree: Uri, kind: ShareKind, path: String, entry: Entry): SharedFile {
        if (entry.directory || entry.size !in 0..maxSize(kind)) throw IOException("File unavailable")
        val uri = document(tree, entry.id)
        val cached = if (kind == ShareKind.Rom) romHashes[uri.toString()]?.takeIf {
            entry.modified > 0 && it.modified == entry.modified && it.size == entry.size
        }?.hash else null
        val fingerprint = cached ?: hash(uri, maxSize(kind))
        val after = stat(tree, entry.id)
        if (after.size != entry.size || after.modified != entry.modified || after.name != entry.name) throw IOException("File changed during transfer")
        if (kind == ShareKind.Rom) romHashes[uri.toString()] = CachedHash(entry.size, entry.modified, fingerprint)
        return SharedFile(path, entry.size, fingerprint)
    }

    fun openRead(folder: String, kind: ShareKind, path: String): InputStream = lock.withLock {
        requireSupported(kind, path)
        requireNoPendingRecovery(folder)
        val tree = tree(folder)
        val entry = find(tree, path) ?: throw IOException("File unavailable")
        if (entry.directory || entry.size !in 0..maxSize(kind)) throw IOException("File unavailable")
        resolver.openInputStream(document(tree, entry.id)) ?: throw IOException("File unavailable")
    }

    fun currentHash(folder: String, kind: ShareKind, path: String): String? = lock.withLock {
        requireSupported(kind, path)
        requireNoPendingRecovery(folder)
        val tree = tree(folder)
        hashAtPath(tree, kind, path)
    }

    private fun hashAtPath(tree: Uri, kind: ShareKind, path: String, guard: () -> Unit = {}): String? {
        requireSupported(kind, path)
        val entry = find(tree, path) ?: return null
        if (entry.directory) throw IOException("Destination is a directory")
        return hash(document(tree, entry.id), maxSize(kind), guard)
    }

    fun stage(kind: ShareKind, source: SharedFile, input: InputStream, progress: (Long) -> Unit = {}): File {
        requireSupported(kind, source.path)
        require(source.size in 0..maxSize(kind))
        cache.mkdirs()
        if (cache.usableSpace < source.size + 8 * 1024 * 1024) throw IOException("Insufficient storage")
        val file = File.createTempFile("incoming-", ".part", cache)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw IOException("Transfer stopped")
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > source.size) throw IOException("File length changed")
                    output.write(buffer, 0, count); digest.update(buffer, 0, count); progress(total)
                }
            }
            if (total != source.size || digest.digest().hex() != source.sha256) throw IOException("File changed during transfer")
            return file
        } catch (error: Exception) { file.delete(); throw error }
    }

    /** Stage and validate the complete transfer before touching a destination save. */
    fun receive(folder: String, kind: ShareKind, source: SharedFile, expectedCurrent: String?, input: InputStream, guard: () -> Unit = {}) {
        val staged = stage(kind, source, input) { guard() }
        try { commit(folder, kind, source, expectedCurrent, staged, guard) } finally { staged.delete() }
    }

    fun commit(folder: String, kind: ShareKind, source: SharedFile, expectedCurrent: String?, staged: File, guard: () -> Unit = {}) = lock.withLock {
        guard()
        requireSupported(kind, source.path)
        require(source.size in 0..maxSize(kind))
        if (staged.length() != source.size || staged.inputStream().use { hash(it, maxSize(kind), guard) } != source.sha256) throw IOException("Invalid staged file")
        recoverPendingLocked(folder)
        val tree = tree(folder)
        val parent = ensureParent(tree, source.path.substringBeforeLast('/', ""))
        val name = source.path.substringAfterLast('/')
        val existing = child(tree, parent, name)
        if (existing?.directory == true) throw IOException("Destination is a directory")
        val existingHash = existing?.let { hash(document(tree, it.id), maxSize(kind), guard) }
        if (existingHash == source.sha256) return@withLock
        if (existing != null && (kind == ShareKind.Rom || isConflictSavePath(source.path))) throw IOException("Destination name collision")
        if (existingHash != expectedCurrent) throw IOException("Destination changed during transfer")
        val transactionId = UUID.randomUUID().toString()
        val incomingName = ".riftdeck-incoming-$transactionId"
        val incoming = DocumentsContract.createDocument(resolver, document(tree, parent), "application/octet-stream", incomingName)
            ?: throw IOException("Cannot create destination")
        var journaled = false
        try {
            if (statUri(incoming).name != incomingName) throw IOException("Temporary name unavailable")
            resolver.openOutputStream(incoming, "wt")?.use { output -> staged.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    guard()
                    val count = input.read(buffer)
                    if (count < 0) break
                    guard()
                    output.write(buffer, 0, count)
                }
            } }
                ?: throw IOException("Cannot write destination")
            if (hash(incoming, maxSize(kind), guard) != source.sha256) throw IOException("Destination write failed")
            var backupPath: String? = null
            if (existing != null) {
                val original = document(tree, existing.id)
                if (hash(original, maxSize(kind), guard) != expectedCurrent) throw IOException("Destination changed during transfer")
                backupPath = conflictSavePath(source.path, requireNotNull(existingHash))
                val backupHash = hashAtPath(tree, kind, backupPath, guard)
                if (backupHash == null) {
                    originalInput(original).use { input ->
                        receive(folder, kind, SharedFile(backupPath, existing.size, existingHash), null, input, guard)
                    }
                } else if (backupHash != existingHash) throw IOException("Backup name collision")
                if (hashAtPath(tree, kind, backupPath, guard) != existingHash) throw IOException("Backup verification failed")
                if (hash(original, maxSize(kind), guard) != expectedCurrent) throw IOException("Destination changed during transfer")
            }
            val recovery = RecoveryRecord(transactionId, folder, kind, source.path, source.sha256, source.size,
                incoming.toString(), incomingName, ".riftdeck-replaced-$transactionId", existingHash, backupPath)
            // AtomicFile fsyncs this private record before either user document is renamed.
            writeRecovery(recovery)
            journaled = true
            if (existing != null) {
                guard()
                DocumentsContract.renameDocument(resolver, document(tree, existing.id), recovery.hiddenOldName)
                    ?: throw IOException("Provider cannot safely replace a file")
                val renamed = child(tree, parent, recovery.hiddenOldName) ?: throw IOException("Original recovery file unavailable")
                if (hash(document(tree, renamed.id), maxSize(kind), guard) != existingHash) throw IOException("Original changed during replacement")
            }
            guard()
            DocumentsContract.renameDocument(resolver, incoming, name)
                ?: throw IOException("Provider cannot safely rename a file")
            // Recovery verifies the canonical checksum and backup before removing a replaced document.
            if (!recover(recovery, guard)) throw IOException("Destination name unavailable")
            romHashes.clear()
        } catch (error: Exception) {
            if (journaled) {
                try { recoverPendingLocked(folder, if (kind == ShareKind.Save) ({}) else guard) }
                catch (recoveryError: Exception) { error.addSuppressed(recoveryError) }
            } else {
                runCatching { DocumentsContract.deleteDocument(resolver, incoming) }
            }
            throw error
        }
    }

    /** Call under the repository's emulator exclusion gate. Missing storage retains its journal. */
    fun recoverPending() = lock.withLock { recoverPendingLocked() }

    private data class RecoveryRecord(
        val id: String,
        val folder: String,
        val kind: ShareKind,
        val path: String,
        val newHash: String,
        val newSize: Long,
        val incomingUri: String,
        val incomingName: String,
        val hiddenOldName: String,
        val oldHash: String?,
        val backupPath: String?,
    )

    private fun requireNoPendingRecovery(folder: String) {
        if (recoveryRecords(folder).isNotEmpty()) throw IOException("Pending transfer must be recovered before reading this folder")
    }

    private fun recoverPendingLocked(folder: String? = null, guard: () -> Unit = {}) {
        recoveryRecords(folder).forEach { recover(it, guard) }
    }

    private fun recoveryRecords(folder: String? = null): List<RecoveryRecord> {
        recoveryDir.listFiles()?.filter { it.name.endsWith(".journal.new") }?.forEach {
            val base = File(recoveryDir, it.name.removeSuffix(".new"))
            if (!base.exists() && !File(recoveryDir, base.name + ".bak").exists()) it.delete()
        }
        val records = recoveryDir.listFiles()?.filter { it.name.endsWith(".journal") || it.name.endsWith(".journal.bak") }
            ?.map { it.name.substringBefore(".journal") }?.distinct()?.sorted().orEmpty()
        return records.mapNotNull { id ->
            val atomic = AtomicFile(File(recoveryDir, "$id.journal"))
            val record = try {
                DataInputStream(atomic.openRead()).use { input ->
                    if (input.readInt() != 1) throw IOException("Unsupported recovery record")
                    RecoveryRecord(id, input.readUTF(), ShareKind.valueOf(input.readUTF()), input.readUTF(), input.readUTF(), input.readLong(),
                        input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF().ifEmpty { null }, input.readUTF().ifEmpty { null })
                }
            } catch (error: Exception) { throw IOException("Cannot read transfer recovery record", error) }
            if (folder != null && record.folder != folder) return@mapNotNull null
            try { requireSupported(record.kind, record.path) } catch (error: IllegalArgumentException) { throw IOException("Invalid transfer recovery path", error) }
            if (record.incomingName != ".riftdeck-incoming-$id" || record.hiddenOldName != ".riftdeck-replaced-$id" ||
                !record.newHash.matches(Regex("[0-9a-f]{64}")) || record.newSize !in 0..maxSize(record.kind) ||
                (record.oldHash == null) != (record.backupPath == null)) throw IOException("Invalid transfer recovery record")
            record
        }
    }

    private fun writeRecovery(record: RecoveryRecord) {
        if (!recoveryDir.exists() && !recoveryDir.mkdirs()) throw IOException("Cannot save transfer recovery record")
        val atomic = AtomicFile(File(recoveryDir, "${record.id}.journal"))
        val output = atomic.startWrite()
        try {
            DataOutputStream(output).apply {
                writeInt(1); writeUTF(record.folder); writeUTF(record.kind.name); writeUTF(record.path); writeUTF(record.newHash); writeLong(record.newSize)
                writeUTF(record.incomingUri); writeUTF(record.incomingName); writeUTF(record.hiddenOldName)
                writeUTF(record.oldHash.orEmpty()); writeUTF(record.backupPath.orEmpty()); flush()
            }
            atomic.finishWrite(output)
        } catch (error: Exception) { atomic.failWrite(output); throw error }
    }

    private fun recover(record: RecoveryRecord, guard: () -> Unit = {}): Boolean {
        val tree = tree(record.folder)
        val parentPath = record.path.substringBeforeLast('/', "")
        val parent = if (parentPath.isEmpty()) DocumentsContract.getTreeDocumentId(tree) else {
            val entry = find(tree, parentPath) ?: throw IOException("Recovery folder unavailable")
            if (!entry.directory) throw IOException("Recovery folder unavailable")
            entry.id
        }
        val name = record.path.substringAfterLast('/')
        val canonical = child(tree, parent, name)
        val hidden = child(tree, parent, record.hiddenOldName)
        val canonicalHash = canonical?.let { if (it.directory) throw IOException("Recovery destination is a directory") else hash(document(tree, it.id), maxSize(record.kind), guard) }
        val committed = canonicalHash == record.newHash && canonical?.size == record.newSize
        when {
            committed -> {
                if (hidden != null) {
                    if (record.oldHash == null || record.backupPath == null ||
                        hashAtPath(tree, record.kind, record.backupPath, guard) != record.oldHash ||
                        hash(document(tree, hidden.id), maxSize(record.kind), guard) != record.oldHash) throw IOException("Original recovery copy must be preserved")
                    guard()
                    if (!DocumentsContract.deleteDocument(resolver, document(tree, hidden.id))) throw IOException("Cannot remove backed-up recovery copy")
                }
            }
            canonical == null && hidden != null -> {
                val originalHash = hash(document(tree, hidden.id), maxSize(record.kind), guard)
                guard()
                DocumentsContract.renameDocument(resolver, document(tree, hidden.id), name)
                    ?: throw IOException("Cannot restore original save")
                val restored = child(tree, parent, name) ?: throw IOException("Original restoration failed")
                if (hash(document(tree, restored.id), maxSize(record.kind), guard) != originalHash) throw IOException("Original restoration failed")
            }
            canonicalHash == record.oldHash && hidden == null -> Unit
            canonical == null && hidden == null && record.oldHash == null -> Unit
            else -> throw IOException("Recovery destination changed; originals retained")
        }
        // Look up the temporary name, since providers may change document IDs during rename.
        val leftover = child(tree, parent, record.incomingName)
        if (leftover != null) {
            if (leftover.directory || hash(document(tree, leftover.id), maxSize(record.kind), guard) != record.newHash) throw IOException("Temporary recovery copy changed")
            guard()
            if (!DocumentsContract.deleteDocument(resolver, document(tree, leftover.id))) throw IOException("Cannot remove incomplete transfer")
        }
        AtomicFile(File(recoveryDir, "${record.id}.journal")).delete()
        return committed
    }

    fun clearStaging() { cache.listFiles()?.filter { it.name.startsWith("incoming-") }?.forEach { it.delete() } }
    private fun originalInput(uri: Uri) = resolver.openInputStream(uri) ?: throw IOException("File unavailable")
    private fun tree(folder: String): Uri = Uri.parse(folder).also { require(DocumentsContract.isTreeUri(it)) }
    private fun document(tree: Uri, id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)
    private fun children(tree: Uri, parent: String): List<Entry> = resolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent), PROJECTION, null, null, null)?.use { cursor ->
        if (cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING)) throw IOException("Folder is still loading")
        buildList {
            while (cursor.moveToNext()) {
                if (size >= 20000) throw IOException("Folder has too many entries")
                add(Entry(cursor.getString(0), cursor.getString(1), cursor.getString(2) == Document.MIME_TYPE_DIR, cursor.getLong(3), cursor.getLong(4)))
            }
        }
    } ?: throw IOException("Folder unavailable")
    private fun stat(tree: Uri, id: String) = statUri(document(tree, id))
    private fun statUri(uri: Uri): Entry = resolver.query(uri, PROJECTION, null, null, null)?.use {
        if (!it.moveToFirst()) throw IOException("File unavailable")
        Entry(it.getString(0), it.getString(1), it.getString(2) == Document.MIME_TYPE_DIR, it.getLong(3), it.getLong(4))
    } ?: throw IOException("File unavailable")
    private fun find(tree: Uri, path: String): Entry? {
        var parent = DocumentsContract.getTreeDocumentId(tree)
        val segments = path.split('/')
        for ((index, segment) in segments.withIndex()) {
            val child = child(tree, parent, segment) ?: return null
            if (index == segments.lastIndex) return child
            if (!child.directory) throw IOException("Invalid directory")
            parent = child.id
        }
        return null
    }
    private fun ensureParent(tree: Uri, path: String): String {
        var parent = DocumentsContract.getTreeDocumentId(tree)
        if (path.isEmpty()) return parent
        for (segment in path.split('/')) {
            val child = child(tree, parent, segment)
            if (child != null) {
                if (!child.directory) throw IOException("Destination is not a folder")
                parent = child.id
            } else {
                val created = DocumentsContract.createDocument(resolver, document(tree, parent), Document.MIME_TYPE_DIR, segment)
                    ?: throw IOException("Cannot create folder")
                if (statUri(created).name != segment) throw IOException("Folder name unavailable")
                parent = DocumentsContract.getDocumentId(created)
            }
        }
        return parent
    }
    private fun child(tree: Uri, parent: String, name: String): Entry? {
        val matching = children(tree, parent).filter { it.name == name }
        if (matching.size > 1) throw IOException("Ambiguous document name")
        return matching.singleOrNull()
    }
    private fun hash(uri: Uri, limit: Long, guard: () -> Unit = {}): String {
        guard()
        return originalInput(uri).use { hash(it, limit, guard) }
    }
    private fun hash(input: InputStream, limit: Long, guard: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(64 * 1024); var length = 0L
        while (true) {
            if (Thread.currentThread().isInterrupted) throw IOException("Transfer stopped")
            guard()
            val count = input.read(buffer); if (count < 0) break
            length += count; if (length > limit) throw IOException("File too large")
            digest.update(buffer, 0, count)
        }
        return digest.digest().hex()
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun requireSupported(kind: ShareKind, path: String) { require(supported(kind, path)) { "Unsupported shared file" } }
    private fun supported(kind: ShareKind, path: String): Boolean = isSafeSharingPath(path) && when (kind) {
        ShareKind.Rom -> path.endsWith(".gba", true) || path.endsWith(".zip", true)
        ShareKind.Save -> isSupportedSavePath(path) || (isConflictSavePath(path) &&
            isSupportedSavePath(path.replace(Regex("\\.riftdeck-conflict-[0-9a-f]{12}"), "")))
    }
    private fun maxSize(kind: ShareKind) = if (kind == ShareKind.Save) MAX_SHARED_SAVE_SIZE else 2L * 1024 * 1024 * 1024
    companion object {
        private val PROJECTION = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
    }
}
