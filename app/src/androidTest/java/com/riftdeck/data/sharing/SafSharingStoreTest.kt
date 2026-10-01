package com.riftdeck.data.sharing

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.riftdeck.core.sharing.ShareKind
import com.riftdeck.core.sharing.SharedFile
import com.riftdeck.core.sharing.conflictSavePath
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SafSharingStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver = context.contentResolver
    private val folder = SharingTestDocumentsProvider.treeUri().toString()
    private val cache = File(context.cacheDir, "sharing-store-test-staging")
    private val recovery = File(context.filesDir, "sharing-store-test-recovery")
    private val original = "original diagnostic save".toByteArray()
    private val incoming = "updated diagnostic save".toByteArray()
    private lateinit var store: SafSharingStore

    @Before fun prepare() {
        control("reset")
        cache.deleteRecursively(); recovery.deleteRecursively()
        store = SafSharingStore(resolver, cache, recovery)
        write("Game.sav", original)
    }

    @After fun cleanup() {
        control("reset")
        cache.deleteRecursively(); recovery.deleteRecursively()
    }

    @Test fun differingRomAndConflictCopiesNeverOverwriteAnExistingFile() {
        write("Game.gba", original)
        fails { store.receive(folder, ShareKind.Rom, descriptor("Game.gba", incoming), digest(original), ByteArrayInputStream(incoming)) }
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Rom, "Game.gba"))
        val path = conflictSavePath("Game.sav", digest(original))
        write(path, original)
        fails { store.receive(folder, ShareKind.Save, descriptor(path, incoming), digest(original), ByteArrayInputStream(incoming)) }
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, path))
        assertNoPendingTransfer()
    }

    @Test fun staleExpectedHashLeavesCanonicalAndBackupsUntouched() {
        fails { store.receive(folder, ShareKind.Save, descriptor("Game.sav", incoming), "0".repeat(64), ByteArrayInputStream(incoming)) }
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, "Game.sav"))
        assertEquals(listOf("Game.sav"), files())
        assertNoPendingTransfer()
    }

    @Test fun checksumMismatchAndDisconnectedStreamsCleanOnlyStaging() {
        fails { store.receive(folder, ShareKind.Save, descriptor("Game.sav", incoming), digest(original), ByteArrayInputStream(original)) }
        val disconnected = object : InputStream() {
            private var readOnce = false
            override fun read(): Int = throw IOException("Disconnected")
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (readOnce) throw IOException("Disconnected")
                readOnce = true
                bytes[offset] = incoming[0]
                return 1
            }
        }
        fails { store.receive(folder, ShareKind.Save, descriptor("Game.sav", incoming), digest(original), disconnected) }
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, "Game.sav"))
        assertEquals(listOf("Game.sav"), files())
        assertNoPendingTransfer()
    }

    @Test fun successfulReplacementKeepsVerifiedOriginalBackupAndCleansJournal() {
        store.receive(folder, ShareKind.Save, descriptor("Game.sav", incoming), digest(original), ByteArrayInputStream(incoming))
        assertEquals(digest(incoming), store.currentHash(folder, ShareKind.Save, "Game.sav"))
        val backup = conflictSavePath("Game.sav", digest(original))
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, backup))
        assertEquals(setOf("Game.sav", backup), files().toSet())
        assertNoPendingTransfer()
    }

    @Test fun incomingRenameFailureImmediatelyRestoresOriginalEvenWhenDocumentIdChanges() {
        flags("failIncomingRename")
        fails { store.receive(folder, ShareKind.Save, descriptor("Game.sav", incoming), digest(original), ByteArrayInputStream(incoming)) }
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, "Game.sav"))
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, conflictSavePath("Game.sav", digest(original))))
        assertNoPendingTransfer()
    }

    @Test fun aNewStoreRestoresOriginalFromDurableJournalAfterInterruptedRenameWindow() {
        flags("failIncomingRename", "failRestoreRename")
        fails { store.receive(folder, ShareKind.Save, descriptor("Game.sav", incoming), digest(original), ByteArrayInputStream(incoming)) }
        assertFalse(files().contains("Game.sav"))
        assertTrue(files().any { it.startsWith(".riftdeck-replaced-") })
        assertTrue(recovery.listFiles().orEmpty().any { it.name.endsWith(".journal") })
        flags()
        fails { store.currentHash(folder, ShareKind.Save, "Game.sav") }
        fails { store.manifest(folder, ShareKind.Save) }
        fails { store.describe(folder, ShareKind.Save, "Game.sav") }
        fails { store.openRead(folder, ShareKind.Save, "Game.sav").close() }
        assertFalse("Read operations must not mutate emulator saves", files().contains("Game.sav"))
        SafSharingStore(resolver, cache, recovery).recoverPending()
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, "Game.sav"))
        assertNoPendingTransfer()
    }

    @Test fun unavailableStorageKeepsRecoveryJournalForTheNextMount() {
        flags("failIncomingRename", "failRestoreRename")
        fails { store.receive(folder, ShareKind.Save, descriptor("Game.sav", incoming), digest(original), ByteArrayInputStream(incoming)) }
        val journalFiles = recovery.listFiles().orEmpty().map { it.name }
        assertTrue(journalFiles.isNotEmpty())
        flags("unavailable")
        fails { SafSharingStore(resolver, cache, recovery).recoverPending() }
        assertEquals(journalFiles, recovery.listFiles().orEmpty().map { it.name })
        flags()
        SafSharingStore(resolver, cache, recovery).recoverPending()
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, "Game.sav"))
        assertNoPendingTransfer()
    }

    @Test fun committedTransferCleansOldHiddenDocumentOnlyWhenBackupIsVerified() {
        flags("failHiddenDelete")
        fails { store.receive(folder, ShareKind.Save, descriptor("Game.sav", incoming), digest(original), ByteArrayInputStream(incoming)) }
        assertEquals(digest(incoming), rawHash("Game.sav"))
        val backup = conflictSavePath("Game.sav", digest(original))
        control("remove", Bundle().apply { putString("path", backup) })
        flags()
        fails { SafSharingStore(resolver, cache, recovery).recoverPending() }
        assertTrue(files().any { it.startsWith(".riftdeck-replaced-") })
        assertTrue(recovery.listFiles().orEmpty().isNotEmpty())
        write(backup, original)
        SafSharingStore(resolver, cache, recovery).recoverPending()
        assertEquals(digest(incoming), store.currentHash(folder, ShareKind.Save, "Game.sav"))
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, backup))
        assertNoPendingTransfer()
    }

    @Test fun nestedDocumentsAreCreatedWithinTheGrantedTreeAndTraversalIsRejected() {
        val path = "gba/core/Game.ss0"
        store.receive(folder, ShareKind.Save, descriptor(path, incoming), null, ByteArrayInputStream(incoming))
        assertEquals(digest(incoming), store.currentHash(folder, ShareKind.Save, path))
        for (unsafe in listOf("../Game.sav", "gba/../Game.sav", "/Game.sav", "gba\\Game.sav")) {
            try {
                store.receive(folder, ShareKind.Save, descriptor(unsafe, incoming), null, ByteArrayInputStream(incoming))
                fail("Unsafe path accepted: $unsafe")
            } catch (_: IllegalArgumentException) { }
        }
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, "Game.sav"))
        assertNoPendingTransfer()
    }

    @Test fun authorizationRevokedBeforeFinalRenameRestoresOriginal() {
        var revoked = false
        fails {
            store.receive(folder, ShareKind.Save, descriptor("Game.sav", incoming), digest(original), ByteArrayInputStream(incoming)) {
                if (files().any { it.startsWith(".riftdeck-replaced-") }) revoked = true
                if (revoked) throw IOException("Sharing stopped")
            }
        }
        assertTrue(revoked)
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, "Game.sav"))
        assertNoPendingTransfer()
        revoked = false
        fails {
            store.receive(folder, ShareKind.Save, descriptor("New.sav", incoming), null, ByteArrayInputStream(incoming)) {
                if (recovery.listFiles().orEmpty().any { it.name.endsWith(".journal") }) revoked = true
                if (revoked) throw IOException("Peer removed")
            }
        }
        assertTrue(revoked)
        assertNull(store.currentHash(folder, ShareKind.Save, "New.sav"))
        assertNoPendingTransfer()
    }

    @Test fun anEmulatorWriteAtTheRenameBoundaryIsRestoredInsteadOfDeleted() {
        val newest = "emulator wrote while the copy was pending".toByteArray()
        var modified = false
        fails {
            store.receive(folder, ShareKind.Save, descriptor("Game.sav", incoming), digest(original), ByteArrayInputStream(incoming)) {
                if (!modified && files().contains(conflictSavePath("Game.sav", digest(original))) &&
                    recovery.listFiles().orEmpty().any { it.name.endsWith(".journal") }) {
                    modified = true
                    write("Game.sav", newest)
                }
            }
        }
        assertTrue(modified)
        assertEquals(digest(newest), store.currentHash(folder, ShareKind.Save, "Game.sav"))
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, conflictSavePath("Game.sav", digest(original))))
        assertNoPendingTransfer()
    }

    @Test fun interruptedInitialJournalWriteNeverTriggersAnOriginalRename() {
        recovery.mkdirs()
        File(recovery, "initial.journal.new").writeBytes(byteArrayOf(1, 2, 3))
        SafSharingStore(resolver, cache, recovery).recoverPending()
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, "Game.sav"))
        assertNoPendingTransfer()
    }

    @Test fun cancellationDuringLargeRomCopyStopsAtAChunkBoundaryBeforeCanonicalRename() {
        val payload = ByteArray(4 * 64 * 1024) { 42 }
        val descriptor = descriptor("Large.gba", payload)
        val staged = store.stage(ShareKind.Rom, descriptor, ByteArrayInputStream(payload))
        var copyChecks = 0
        try {
            fails {
                store.commit(folder, ShareKind.Rom, descriptor, null, staged) {
                    if (files().any { it.startsWith(".riftdeck-incoming-") } && ++copyChecks >= 4) throw IOException("Emulator launch requested")
                }
            }
        } finally { staged.delete() }
        assertEquals(4, copyChecks)
        assertFalse(files().contains("Large.gba"))
        assertEquals(digest(original), store.currentHash(folder, ShareKind.Save, "Game.sav"))
        assertNoPendingTransfer()
    }

    @Test fun romCatalogListsStatsWithoutReadingAllPayloadsAndDescribeHashesSelectedRom() {
        write("gba/One.gba", original); write("gba/Two.zip", incoming)
        val before = control("stats")!!.getInt("reads")
        val catalog = store.manifest(folder, ShareKind.Rom)
        assertEquals(listOf("gba/One.gba", "gba/Two.zip"), catalog.map { it.path })
        assertTrue(catalog.all { it.sha256 == "0".repeat(64) })
        assertEquals(before, control("stats")!!.getInt("reads"))
        assertEquals(descriptor("gba/One.gba", original), store.describe(folder, ShareKind.Rom, "gba/One.gba"))
        assertEquals(before + 1, control("stats")!!.getInt("reads"))
        store.describe(folder, ShareKind.Rom, "gba/One.gba")
        assertEquals(before + 1, control("stats")!!.getInt("reads"))
    }

    private fun descriptor(path: String, bytes: ByteArray) = SharedFile(path, bytes.size.toLong(), digest(bytes))
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun write(path: String, bytes: ByteArray) = control("write", Bundle().apply { putString("path", path); putByteArray("bytes", bytes) })
    private fun flags(vararg names: String) = control("flags", Bundle().apply { names.forEach { putBoolean(it, true) } })
    private fun control(method: String, extras: Bundle? = null): Bundle? = resolver.call(Uri.parse("content://com.riftdeck.tests.sharing.control"), method, null, extras)
    private fun files(): List<String> = control("stats")!!.getStringArrayList("files")!!.sorted()
    private fun rawHash(path: String): String = resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(
        SharingTestDocumentsProvider.treeUri(), "${SharingTestDocumentsProvider.ROOT_ID}/$path"))!!.use { digest(it.readBytes()) }
    private fun assertNoPendingTransfer() {
        assertFalse(files().any { it.startsWith(".riftdeck-incoming-") || it.startsWith(".riftdeck-replaced-") })
        assertTrue(recovery.listFiles().orEmpty().isEmpty())
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }
    private fun fails(block: () -> Unit) {
        try { block(); fail("Expected safe transfer failure") } catch (_: IOException) { }
    }
}
