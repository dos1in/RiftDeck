package com.riftdeck.data.scanner

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.riftdeck.data.database.LibraryDatabase
import com.riftdeck.data.sharing.SharingTestDocumentsProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SafRomDocumentSourceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver = context.contentResolver
    private val folder = SharingTestDocumentsProvider.treeUri().toString()
    private lateinit var database: LibraryDatabase
    private lateinit var scanner: LibraryScanner
    private val dao get() = database.games()

    @Before fun prepare() {
        control("reset")
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        scanner = LibraryScanner(SafRomDocumentSource(resolver), dao)
        write("Game.gba", "diagnostic ROM")
    }

    @After fun cleanup() {
        database.close()
        control("reset")
    }

    @Test fun providerErrorInAnEmptyListingPreservesGamesAndHistory() = runBlocking {
        scanner.scan(listOf(folder))
        val before = dao.observeGames().first().single()
        dao.toggleFavorite(before.id)
        dao.recordLaunch(before.id, 123L)
        listingError("")
        scanner.scan(listOf(folder))

        val after = dao.observeGames().first().single()
        assertEquals(before.id, after.id)
        assertTrue(after.favorite)
        assertEquals(1, after.playCount)
        assertEquals(123L, after.lastPlayedAt)
        assertEquals(setOf(folder), scanner.state.value.failedFolders)
        assertFalse(scanner.state.value.running)

        // Removing the actual file after the provider recovers remains a valid empty scan.
        control("flags")
        control("remove", Bundle().apply { putString("path", "Game.gba") })
        scanner.scan(listOf(folder))
        assertTrue(scanner.state.value.failedFolders.isEmpty())
        assertTrue(dao.observeGames().first().isEmpty())
    }

    @Test fun artworkDirectoryErrorCannotTurnARescanIntoADeletion() = runBlocking {
        write("media/Game/boxFront.png", "diagnostic artwork")
        scanner.scan(listOf(folder))
        val before = dao.observeGames().first().single()
        assertNotNull(before.coverUri)
        listingError("media")
        scanner.scan(listOf(folder))

        assertEquals(before, dao.observeGames().first().single())
        assertEquals(setOf(folder), scanner.state.value.failedFolders)
    }

    @Test fun descriptionsLoadWithoutAUsableImplicitMediaPath() = runBlocking {
        write("metadata.pegasus.txt", "game: Game: Subtitle\nfile: Game.gba\ndescription: A useful introduction\n  A second line")
        scanner.scan(listOf(folder))

        val game = dao.observeGames().first().single()
        assertEquals("A useful introduction\nA second line", game.description)
        assertNull(game.coverUri)
        assertNull(game.videoUri)
        assertTrue(scanner.state.value.failedFolders.isEmpty())
    }

    @Test fun aDirectoryNamedMediaStillImportsItsRomFiles() = runBlocking {
        write("media/Nested.gba", "nested ROM")
        scanner.scan(listOf(folder))

        assertEquals(setOf("Game.gba", "Nested.gba"), dao.observeGames().first().map { it.fileName }.toSet())
        assertTrue(scanner.state.value.failedFolders.isEmpty())
    }

    @Test fun descriptionsKeepSubdirectoryGamesWithTheSameFilenameDistinct() = runBlocking {
        write("volume1/Game.gba", "first ROM")
        write("volume2/Game.gba", "second ROM")
        write("metadata.pegasus.txt", "game: First: Volume\nfile: volume1/Game.gba\ndescription: First introduction\n" +
            "game: Second: Volume\nfile: volume2/Game.gba\ndescription: Second introduction")
        scanner.scan(listOf(folder))

        val games = dao.observeGames().first().associateBy { it.identity.substringAfterLast("fixture-root/") }
        assertEquals("First introduction", games.getValue("volume1/Game.gba").description)
        assertEquals("Second introduction", games.getValue("volume2/Game.gba").description)
        assertNull(games.getValue("Game.gba").description)
        assertTrue(scanner.state.value.failedFolders.isEmpty())
    }

    private fun write(path: String, text: String) = control("write", Bundle().apply {
        putString("path", path)
        putByteArray("bytes", text.toByteArray())
    })

    private fun listingError(path: String) = control("flags", Bundle().apply { putString("listingErrorPath", path) })

    private fun control(method: String, extras: Bundle? = null): Bundle? =
        resolver.call(Uri.parse("content://com.riftdeck.tests.sharing.control"), method, null, extras)
}
