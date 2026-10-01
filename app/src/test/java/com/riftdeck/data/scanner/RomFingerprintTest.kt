package com.riftdeck.data.scanner

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class RomFingerprintTest {
    private val payload = "original diagnostic ROM".toByteArray()

    @Test fun rawAndCompressedCopiesHaveTheSamePayloadFingerprint() {
        val raw = romFingerprint(ByteArrayInputStream(payload), "Game.gba")
        val zipped = romFingerprint(ByteArrayInputStream(archive("nested/Game.GBA" to payload)), "Game.zip")
        assertEquals(raw, zipped)
        assertNotEquals(raw, romFingerprint(ByteArrayInputStream(payload + 1.toByte()), "Game.gba"))
    }

    @Test fun ambiguousUnsafeAndNonRomArchivesRemainSeparate() {
        for (bytes in listOf(archive("one.gba" to payload, "two.gba" to payload),
            archive("../Game.gba" to payload), archive("image.png" to payload))) {
            assertNull(romFingerprint(ByteArrayInputStream(bytes), "Game.zip"))
        }
    }

    @Test fun emptyAndCorruptPayloadsCannotBecomeDuplicateIdentities() {
        try { romFingerprint(ByteArrayInputStream(byteArrayOf()), "Game.gba"); fail("Expected empty ROM failure") }
        catch (_: IOException) { }
        val bytes = archive("Game.gba" to payload)
        // Truncate inside the compressed payload rather than only removing the central directory.
        try { romFingerprint(ByteArrayInputStream(bytes.copyOf(45)), "Game.zip"); fail("Expected corrupt ZIP failure") }
        catch (_: IOException) { }
    }

    @Test fun fingerprintingRespondsToCancellation() {
        try {
            romFingerprint(ByteArrayInputStream(payload), "Game.gba") { throw CancellationException("cancel") }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }

    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()
}
