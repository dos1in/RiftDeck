package com.riftdeck.core.emulator

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.riftdeck.core.model.Game
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class PreparedRom(val uri: Uri, val name: String)

/** Leaves SAF files in place; archives use private, temporarily shared cache files. */
class RomLaunchPreparer(private val context: Context) {
    suspend fun prepare(game: Game): PreparedRom = withContext(Dispatchers.IO) {
        val source = Uri.parse(game.romUri)
        if (source.scheme != "content") throw FileNotFoundException("Unsupported ROM location")
        when (game.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "gba" -> {
                context.contentResolver.openInputStream(source)?.use {
                    if (it.read() < 0) throw FileNotFoundException("Empty ROM")
                } ?: throw FileNotFoundException("ROM unavailable")
                PreparedRom(source, game.fileName)
            }
            "zip" -> prepareArchive(source)
            else -> throw ArchiveException(ArchiveFailure.NoRom)
        }
    }

    private suspend fun prepareArchive(source: Uri): PreparedRom {
        clearExpiredFiles()
        val directory = File(context.cacheDir, "rom_launches/${UUID.randomUUID()}")
        if (!directory.mkdirs()) throw java.io.IOException("Cannot create ROM cache")
        var prepared = false
        try {
            val coroutine = currentCoroutineContext()
            val archive = File(directory, "source.zip")
            context.contentResolver.openInputStream(source)?.use { input ->
                archive.outputStream().use { output ->
                    copyBounded(input, output, 64L * 1024 * 1024, checkCancelled = { coroutine.ensureActive() })
                }
            } ?: throw FileNotFoundException("ROM archive unavailable")
            val reader = GbaArchive()
            val entry = reader.inspect(archive)
            val target = File(directory, entry.name)
            reader.extract(archive, target) { coroutine.ensureActive() }
            archive.delete()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.roms", target)
            prepared = true
            return PreparedRom(uri, entry.name)
        } finally {
            if (!prepared) directory.deleteRecursively()
        }
    }

    /** Keep recent files for emulator task restoration; Android may also reclaim the cache. */
    suspend fun clearExpiredFiles() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        File(context.cacheDir, "rom_launches").listFiles()?.forEach { directory ->
            currentCoroutineContext().ensureActive()
            if (directory.isDirectory && directory.lastModified() < cutoff) directory.deleteRecursively()
        }
    }
}
