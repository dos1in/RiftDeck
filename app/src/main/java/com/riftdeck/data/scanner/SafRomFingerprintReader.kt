package com.riftdeck.data.scanner

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SafRomFingerprintReader(private val resolver: ContentResolver) : RomFingerprintReader {
    override suspend fun read(document: RomDocument): RomFingerprint? = withContext(Dispatchers.IO) {
        if (document.size > 64L * 1024 * 1024) return@withContext null
        val coroutine = currentCoroutineContext()
        resolver.openInputStream(Uri.parse(document.uri))?.use { input ->
            romFingerprint(input, document.name) { coroutine.ensureActive() }
        }
    }
}
