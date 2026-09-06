package com.riftdeck.core.emulator

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

enum class ArchiveFailure { Invalid, TooLarge, NoRom, MultipleRoms, UnsafeName }
class ArchiveException(val reason: ArchiveFailure) : IOException(reason.name)

data class ArchiveRom(val name: String, val size: Long)

/** Small, single-ROM ZIPs only. Entry paths never become destination paths. */
class GbaArchive(
    private val maxArchiveBytes: Long = 64L * 1024 * 1024,
    private val maxRomBytes: Long = 64L * 1024 * 1024,
    private val maxEntries: Int = 256,
) {
    fun inspect(archive: File): ArchiveRom {
        checkDirectory(archive)
        return ZipFile(archive).use { zip ->
            val entry = selectRom(zip)
            ArchiveRom(entry.name.substringAfterLast('/'), entry.size)
        }
    }

    fun extract(archive: File, destination: File, checkCancelled: () -> Unit = {}): ArchiveRom {
        checkDirectory(archive)
        val parent = destination.absoluteFile.parentFile ?: throw IOException("Missing destination directory")
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create destination directory")
        val temporary = File.createTempFile("rom-", ".part", parent)
        try {
            return ZipFile(archive).use { zip ->
                val entry = selectRom(zip)
                val crc = CRC32()
                val count = zip.getInputStream(entry).use { input ->
                    temporary.outputStream().use { output ->
                        copyBounded(input, output, maxRomBytes, checkCancelled) { bytes, length -> crc.update(bytes, 0, length) }
                    }
                }
                if (count == 0L || count != entry.size || crc.value != entry.crc) throw ArchiveException(ArchiveFailure.Invalid)
                checkCancelled()
                // No replacement: a second launch must not overwrite a file in use by an emulator.
                Files.move(temporary.toPath(), destination.toPath())
                ArchiveRom(entry.name.substringAfterLast('/'), count)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun selectRom(zip: ZipFile): ZipEntry {
        if (zip.size() > maxEntries) throw ArchiveException(ArchiveFailure.TooLarge)
        var selected: ZipEntry? = null
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory || entry.name.startsWith("__MACOSX/") ||
                !entry.name.lowercase(Locale.ROOT).endsWith(".gba")) continue
            if (entry.name.startsWith('/') || entry.name.contains('\\') || entry.name.contains(':') ||
                entry.name.contains('\u0000') || entry.name.split('/').any { it == ".." }) {
                throw ArchiveException(ArchiveFailure.UnsafeName)
            }
            if (selected != null) throw ArchiveException(ArchiveFailure.MultipleRoms)
            if (entry.size < 1) throw ArchiveException(ArchiveFailure.Invalid)
            if (entry.size > maxRomBytes) throw ArchiveException(ArchiveFailure.TooLarge)
            selected = entry
        }
        return selected ?: throw ArchiveException(ArchiveFailure.NoRom)
    }

    /** Reject huge/ZIP64 central directories before ZipFile allocates its entry index. */
    private fun checkDirectory(archive: File) {
        val size = archive.length()
        if (size > maxArchiveBytes) throw ArchiveException(ArchiveFailure.TooLarge)
        if (size < 22) throw ArchiveException(ArchiveFailure.Invalid)
        RandomAccessFile(archive, "r").use { file ->
            val tail = ByteArray(minOf(size, 65_557L).toInt())
            file.seek(size - tail.size)
            file.readFully(tail)
            fun u16(at: Int) = (tail[at].toInt() and 255) or ((tail[at + 1].toInt() and 255) shl 8)
            fun u32(at: Int) = u16(at).toLong() or (u16(at + 2).toLong() shl 16)
            for (at in tail.size - 22 downTo 0) {
                if (u16(at) != 0x4b50 || u16(at + 2) != 0x0605 || at + 22 + u16(at + 20) != tail.size) continue
                if (u16(at + 4) != 0 || u16(at + 6) != 0 || u16(at + 8) != u16(at + 10)) {
                    throw ArchiveException(ArchiveFailure.Invalid)
                }
                val count = u16(at + 10)
                val directorySize = u32(at + 12)
                val directoryOffset = u32(at + 16)
                if (count == 65_535 || count > maxEntries || directorySize == 0xffffffffL ||
                    directoryOffset == 0xffffffffL || directorySize > maxEntries.toLong() * 4096) {
                    throw ArchiveException(ArchiveFailure.TooLarge)
                }
                if (directoryOffset + directorySize > size - tail.size + at) throw ArchiveException(ArchiveFailure.Invalid)
                return
            }
        }
        throw ArchiveException(ArchiveFailure.Invalid)
    }
}

fun copyBounded(input: InputStream, output: OutputStream, limit: Long,
    checkCancelled: () -> Unit = {}, onBytes: (ByteArray, Int) -> Unit = { _, _ -> }): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        checkCancelled()
        val read = input.read(buffer)
        if (read < 0) return total
        if (read == 0) continue
        if (read.toLong() > limit - total) throw ArchiveException(ArchiveFailure.TooLarge)
        output.write(buffer, 0, read)
        onBytes(buffer, read)
        total += read
    }
}
