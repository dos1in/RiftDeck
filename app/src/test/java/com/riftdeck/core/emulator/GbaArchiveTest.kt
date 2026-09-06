package com.riftdeck.core.emulator

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GbaArchiveTest {
    @get:Rule val folder = TemporaryFolder()
    private fun zip(vararg entries: Pair<String, ByteArray>): File = folder.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
    }
    private fun rejected(reason: ArchiveFailure, block: () -> Unit) {
        try { block(); fail("Expected $reason") }
        catch (error: ArchiveException) { assertEquals(reason, error.reason) }
    }

    @Test fun extractsSingleNestedRomToExactDestination() {
        val bytes = ByteArray(1024) { it.toByte() }
        val archive = zip("folder/Game.GBA" to bytes, "readme.txt" to byteArrayOf(1))
        val output = File(folder.root, "launch.gba")
        val result = GbaArchive().extract(archive, output)
        assertEquals("Game.GBA", result.name)
        assertArrayEquals(bytes, output.readBytes())
        assertFalse(File(folder.root, "folder").exists())
    }

    @Test fun rejectsAmbiguousAndNonRomArchives() {
        rejected(ArchiveFailure.MultipleRoms) { GbaArchive().inspect(zip("a.gba" to byteArrayOf(1), "b.gba" to byteArrayOf(2))) }
        rejected(ArchiveFailure.NoRom) { GbaArchive().inspect(zip("notes.txt" to byteArrayOf(1))) }
    }

    @Test fun rejectsPathTraversalWithoutWritingOutsideDestination() {
        val archive = zip("../escape.gba" to byteArrayOf(1))
        val output = File(folder.root, "rom.gba")
        rejected(ArchiveFailure.UnsafeName) { GbaArchive().extract(archive, output) }
        assertFalse(output.exists())
        assertTrue(folder.root.listFiles()!!.none { it.extension == "part" })
    }

    @Test fun rejectsOversizedPayloadAndExcessiveEntryCount() {
        rejected(ArchiveFailure.TooLarge) { GbaArchive(maxRomBytes = 8).inspect(zip("a.gba" to ByteArray(9))) }
        rejected(ArchiveFailure.TooLarge) { GbaArchive(maxEntries = 1).inspect(zip("a.gba" to byteArrayOf(1), "readme" to byteArrayOf(1))) }
        val output = ByteArrayOutputStream()
        rejected(ArchiveFailure.TooLarge) { copyBounded(ByteArrayInputStream(ByteArray(100)), output, 10) }
        assertEquals(0, output.size())
    }

    @Test fun cancellationRemovesPartialOutputAndPreservesExistingFiles() {
        val archive = zip("a.gba" to ByteArray(1024))
        val output = File(folder.root, "rom.gba")
        try { GbaArchive().extract(archive, output) { throw IOException("Canceled") }; fail() }
        catch (_: IOException) { }
        assertFalse(output.exists())
        assertTrue(folder.root.listFiles()!!.none { it.extension == "part" })
        output.writeText("existing")
        try { GbaArchive().extract(archive, output); fail() }
        catch (_: IOException) { }
        assertEquals("existing", output.readText())
    }

    @Test fun crcMismatchRejectsCorruptedPayloadAndRemovesTemporaryFile() {
        val data = byteArrayOf(1, 2, 3, 4)
        val archive = folder.newFile()
        ZipOutputStream(archive.outputStream()).use { zip ->
            val entry = ZipEntry("test.gba").apply {
                method = ZipEntry.STORED
                size = data.size.toLong()
                compressedSize = size
                crc = java.util.zip.CRC32().apply { update(data) }.value
            }
            zip.putNextEntry(entry); zip.write(data); zip.closeEntry()
        }
        val bytes = archive.readBytes()
        val nameLength = (bytes[26].toInt() and 255) or ((bytes[27].toInt() and 255) shl 8)
        val extraLength = (bytes[28].toInt() and 255) or ((bytes[29].toInt() and 255) shl 8)
        bytes[30 + nameLength + extraLength] = 9
        archive.writeBytes(bytes)
        val output = File(folder.root, "rom.gba")
        rejected(ArchiveFailure.Invalid) { GbaArchive().extract(archive, output) }
        assertFalse(output.exists())
        assertTrue(folder.root.listFiles()!!.none { it.extension == "part" })
    }

    @Test fun corruptOrEmptyArchivesFailBeforeCreatingOutput() {
        val invalid = folder.newFile().apply { writeText("not a zip") }
        rejected(ArchiveFailure.Invalid) { GbaArchive().inspect(invalid) }
        rejected(ArchiveFailure.Invalid) { GbaArchive().inspect(zip("empty.gba" to byteArrayOf())) }
    }
}
