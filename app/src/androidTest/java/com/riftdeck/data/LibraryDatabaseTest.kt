package com.riftdeck.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.riftdeck.data.database.LibraryDatabase
import com.riftdeck.data.scanner.LibraryScanner
import com.riftdeck.data.scanner.RomDocument
import com.riftdeck.data.scanner.RomDocumentSource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LibraryDatabaseTest {
    private lateinit var database: LibraryDatabase
    private val dao get() = database.games()
    private val files = mutableMapOf<String, List<RomDocument>>()
    private var failAfter: Int? = null
    private var cancel = false
    private lateinit var scanner: LibraryScanner

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LibraryDatabase::class.java).build()
        scanner = LibraryScanner(object : RomDocumentSource {
            override suspend fun enumerate(folder: String, onDocument: suspend (RomDocument) -> Unit) {
                files.getValue(folder).forEachIndexed { index, file ->
                    if (failAfter == index) {
                        if (cancel) throw CancellationException("Test cancellation")
                        throw IOException("Storage disconnected")
                    }
                    onDocument(file)
                }
            }
        }, dao)
    }

    @After fun close() { database.close() }

    private fun file(id: Int, tree: String = "a") = RomDocument("provider:rom/$id", "content://provider/tree/$tree/document/rom/$id",
        "Game_$id.gba", 1024, 1, 1)

    @Test fun rescanRefreshesIntroductionAndVideoWithoutLosingFavorites() = runBlocking {
        files["a"] = listOf(file(1).copy(description = "Old", videoUri = "content://video/old"))
        scanner.scan(listOf("a"))
        val id = dao.findByIdentity("provider:rom/1")!!.id
        dao.toggleFavorite(id)
        files["a"] = listOf(file(1).copy(description = "New", videoUri = "content://video/new"))
        scanner.scan(listOf("a"))
        val game = dao.findById(id)!!.toGame()
        assertEquals("New", game.description)
        assertEquals("content://video/new", game.videoUri)
        assertTrue(game.favorite)
        files["a"] = listOf(file(1))
        scanner.scan(listOf("a"))
        assertNull(dao.findById(id)!!.description)
        assertNull(dao.findById(id)!!.videoUri)
    }

    @Test fun incrementalScanPreservesFavoriteHistoryAndStableIds() = runBlocking {
        files["a"] = (1..120).map { file(it) }
        scanner.scan(listOf("a"))
        val game = dao.findByIdentity("provider:rom/1")!!
        dao.toggleFavorite(game.id)
        dao.recordLaunch(game.id, 1000)
        dao.addPlayTime(game.id, 120)
        files["a"] = listOf(file(1).copy(size = 2048, modifiedAt = 2), file(121))
        scanner.scan(listOf("a"))
        val updated = dao.findByIdentity(game.identity)!!
        assertEquals(game.id, updated.id)
        assertTrue(updated.favorite)
        assertEquals(1, updated.playCount)
        assertEquals(120L, updated.playTimeSeconds)
        assertEquals(1000L, updated.lastPlayedAt)
        assertEquals(2048L, updated.fileSize)
        assertEquals(2, dao.observeGames().first().size)
        assertTrue(scanner.state.value.completed)
    }

    @Test fun overlappingTreesDeduplicateAndRetainAWorkingUriWhenOneIsRemoved() = runBlocking {
        files["a"] = listOf(file(1))
        files["b"] = listOf(file(1, "b"))
        scanner.scan(listOf("a", "b"))
        val before = dao.observeGames().first().single()
        dao.toggleFavorite(before.id)
        scanner.removeFolder("a")
        val after = dao.observeGames().first().single()
        assertEquals(before.id, after.id)
        assertTrue(after.favorite)
        assertEquals(file(1, "b").uri, after.romUri)
    }

    @Test fun partialProviderFailureNeverDeletesPreviouslyImportedGames() = runBlocking {
        files["a"] = (1..120).map { file(it) }
        scanner.scan(listOf("a"))
        failAfter = 51
        scanner.scan(listOf("a"))
        assertEquals(120, dao.observeGames().first().size)
        assertEquals(setOf("a"), scanner.state.value.failedFolders)
        assertFalse(scanner.state.value.running)
    }

    @Test fun accessibleOverlappingTreeReplacesAnUnchangedStaleUri() = runBlocking {
        files["a"] = listOf(file(1))
        files["b"] = listOf(file(1, "b"))
        scanner.scan(listOf("a"))
        val before = dao.observeGames().first().single()
        dao.toggleFavorite(before.id)
        scanner.scan(listOf("b"))
        val after = dao.observeGames().first().single()
        assertEquals(before.id, after.id)
        assertTrue(after.favorite)
        assertEquals(file(1, "b").uri, after.romUri)
    }

    @Test fun canceledScanKeepsOldGamesAndStopsProgress() = runBlocking {
        files["a"] = (1..120).map { file(it) }
        scanner.scan(listOf("a"))
        failAfter = 51
        cancel = true
        try { scanner.scan(listOf("a")); fail("Expected cancellation") }
        catch (_: CancellationException) { }
        assertEquals(120, dao.observeGames().first().size)
        assertFalse(scanner.state.value.running)
        assertFalse(scanner.state.value.completed)
    }

    @Test fun successfulEmptyRescanRemovesDeletedFiles() = runBlocking {
        files["a"] = listOf(file(1))
        scanner.scan(listOf("a"))
        files["a"] = emptyList()
        scanner.scan(listOf("a"))
        assertTrue(dao.observeGames().first().isEmpty())
    }

    @Test fun sidecarChangesRefreshArtworkWithoutResettingGameState() = runBlocking {
        files["a"] = listOf(file(1).copy(coverUri = "content://provider/cover", coverVersion = "1:500"))
        scanner.scan(listOf("a"))
        val game = dao.observeGames().first().single()
        dao.toggleFavorite(game.id)
        dao.recordLaunch(game.id, 100)
        files["a"] = listOf(file(1).copy(coverUri = game.coverUri, coverVersion = "2:600"))
        scanner.scan(listOf("a"))
        val updated = dao.findById(game.id)!!
        assertEquals("2:600", updated.toGame().coverVersion)
        assertTrue(updated.favorite)
        assertEquals(1, updated.playCount)
        files["a"] = listOf(file(1))
        scanner.scan(listOf("a"))
        assertNull(dao.findById(game.id)!!.coverUri)
        assertTrue(dao.findById(game.id)!!.favorite)
    }

    @Test fun largeLibraryRemainsUniqueAcrossRepeatedScans() = runBlocking {
        files["a"] = (1..5000).map { file(it) }
        scanner.scan(listOf("a"))
        val before = dao.observeGames().first().map { it.id }
        scanner.scan(listOf("a"))
        assertEquals(before, dao.observeGames().first().map { it.id })
        assertEquals(5000, scanner.state.value.discovered)
    }
    @Test fun playSessionIsSettledOnlyOnceAndIgnoresStaleResume() = runBlocking {
        files["a"] = listOf(file(1))
        scanner.scan(listOf("a"))
        val game = dao.observeGames().first().single()
        dao.beginSession(game.id, 9000, 1000, 3)
        dao.finishSession(500, 3)
        assertNotNull(dao.session())
        dao.finishSession(14000, 3)
        dao.finishSession(25000, 3)
        val played = dao.findById(game.id)!!
        assertEquals(1, played.playCount)
        assertEquals(9000L, played.lastPlayedAt)
        assertEquals(13L, played.playTimeSeconds)
        assertNull(dao.session())
    }

    @Test fun versionOneMigrationPreservesLibraryAndSupportsSessions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-smoke.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val schema = org.json.JSONObject(testContext.assets.open("com.riftdeck.data.database.LibraryDatabase/1.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.getJSONArray("indices")
                for (i in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(i).getString("createSql")
                    .replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
            sqlite.execSQL("INSERT INTO games (id, identity, platformId, title, sortTitle, romUri, fileName, fileSize, modifiedAt, favorite, hidden, playCount, playTimeSeconds) VALUES (1, 'legacy', 1, 'Legacy', 'legacy', 'content://test/1', 'legacy.gba', 1024, 1, 1, 0, 4, 120)")
            sqlite.version = 1
        }
        val upgraded = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
        try {
            val game = upgraded.games().observeGames().first().single()
            assertTrue(game.favorite)
            assertEquals(4, game.playCount)
            assertEquals(120L, game.playTimeSeconds)
            upgraded.games().beginSession(game.id, 100, 1000, 1)
            upgraded.games().finishSession(5000, 1)
            assertEquals(124L, upgraded.games().findById(game.id)!!.playTimeSeconds)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }

}
