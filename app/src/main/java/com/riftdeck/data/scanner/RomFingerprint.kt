package com.riftdeck.data.scanner

import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

data class RomFingerprint(val crc32: String, val sha1: String)

/** Only used for suspected copies, never for every file in the library. */
fun interface RomFingerprintReader {
    suspend fun read(document: RomDocument): RomFingerprint?
}

/** Hash the ROM payload so a raw ROM and its single-ROM ZIP have the same identity. */
fun romFingerprint(input: InputStream, name: String, checkCancelled: () -> Unit = {}): RomFingerprint? {
    val limit = 64L * 1024 * 1024
    fun hash(stream: InputStream): RomFingerprint {
        val crc = CRC32()
        val digest = MessageDigest.getInstance("SHA-1")
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        while (true) {
            checkCancelled()
            val count = stream.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            size += count
            if (size > limit) throw IOException("ROM exceeds fingerprint limit")
            crc.update(buffer, 0, count)
            digest.update(buffer, 0, count)
        }
        if (size == 0L) throw IOException("Empty ROM")
        return RomFingerprint(crc.value.toString(16).padStart(8, '0'),
            digest.digest().joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 255) })
    }
    return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "gba" -> hash(input)
        "zip" -> ZipInputStream(input).use { zip ->
            var fingerprint: RomFingerprint? = null
            var entries = 0
            while (true) {
                checkCancelled()
                val entry = zip.nextEntry ?: break
                if (++entries > 256) throw IOException("Too many archive entries")
                // Do not decompress unrelated payloads just to look for a ROM.
                if (entry.isDirectory) {
                    if (zip.read() != -1) return null
                    continue
                }
                if (!entry.name.endsWith(".gba", ignoreCase = true) || entry.name.startsWith("__MACOSX/")) return null
                if (fingerprint != null || entry.name.startsWith('/') || entry.name.contains('\\') ||
                    entry.name.contains(':') || entry.name.contains('\u0000') || entry.name.split('/').any { it == ".." }) return null
                fingerprint = hash(zip)
            }
            fingerprint
        }
        else -> null
    }
}
