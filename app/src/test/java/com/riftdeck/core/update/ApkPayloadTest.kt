package com.riftdeck.core.update

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkPayloadTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun releaseDigestAndExactLengthAreBothRequired() {
        val file = folder.newFile("update.apk").apply { writeBytes("apk bytes".toByteArray()) }
        val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val release = UpdateRelease("v1.0.0", "1.0.0", ReleaseVersion(1, 0, 0), "", "https://github.com", "https://github.com/app.apk", file.length(), hash)
        verifyUpdatePayload(file, release)
        for (bad in listOf(release.copy(sha256 = "0".repeat(64)), release.copy(apkSize = file.length() + 1),
            release.copy(apkSize = MAX_UPDATE_APK_BYTES + 1))) {
            try { verifyUpdatePayload(file, bad); fail("Invalid payload accepted") }
            catch (error: ApkValidationException) { assertEquals(ApkValidationFailure.Payload, error.failure) }
        }
    }

    @Test fun cancellationStopsHashingBeforeLargeFileIsRead() {
        val file = folder.newFile().apply { writeBytes(ByteArray(128 * 1024)) }
        val release = UpdateRelease("v1.0.0", "1.0.0", ReleaseVersion(1, 0, 0), "", "https://github.com", "https://github.com/app.apk", file.length(), "0".repeat(64))
        try { verifyUpdatePayload(file, release) { throw InterruptedException("cancelled") }; fail("Cancelled hash continued") }
        catch (_: InterruptedException) { }
    }
}
