package com.riftdeck.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.riftdeck.core.emulator.RomLaunchPreparer
import com.riftdeck.data.database.LibraryDatabase
import com.riftdeck.data.scanner.LibraryScanner
import com.riftdeck.data.scanner.SafRomDocumentSource
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in test: revokes the supplied test directory grant; reauthorize it through the picker afterward. */
class SafPermissionIntegrationTest {
    @Test fun revokedTreePreservesLibraryAndRejectsRomRead() = runBlocking {
        val tree = InstrumentationRegistry.getArguments().getString("permissionTestTree")
        assumeTrue("Requires an explicitly supplied test directory", tree != null)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver
        val uri = Uri.parse(tree!!)
        assertTrue("Authorize the test directory through the app before running", resolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        })
        val database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        try {
            val dao = database.games()
            val scanner = LibraryScanner(SafRomDocumentSource(resolver), dao)
            scanner.scan(listOf(tree))
            assertTrue(scanner.state.value.failedFolders.isEmpty())
            val before = dao.observeGames().first()
            assertTrue("Test directory must contain a raw GBA ROM", before.any { it.fileName.endsWith(".gba", true) })
            val game = before.first { it.fileName.endsWith(".gba", true) }
            dao.toggleFavorite(game.id)
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            assertFalse(resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission })
            scanner.scan(listOf(tree))
            assertEquals(setOf(tree), scanner.state.value.failedFolders)
            assertFalse(scanner.state.value.running)
            assertEquals(before.map { it.id }, dao.observeGames().first().map { it.id })
            assertTrue(dao.findById(game.id)!!.favorite)
            try {
                RomLaunchPreparer(context).prepare(game.toGame())
                fail("A revoked grant must not allow the ROM to be read")
            } catch (_: SecurityException) {
                // Android denies access to a document without the tree grant.
            } catch (_: IOException) {
                // Some providers translate lost permission into a file-access error.
            }
        } finally { database.close() }
    }
}
