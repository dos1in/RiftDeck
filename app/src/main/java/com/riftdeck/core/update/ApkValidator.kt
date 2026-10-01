package com.riftdeck.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest

enum class ApkValidationFailure { Payload, Package, Version, Platform, Signature }
class ApkValidationException(val failure: ApkValidationFailure) : IOException("Invalid update APK: $failure")

/** Call on an I/O dispatcher, including immediately before handing a file to Android's installer. */
class ApkValidator(context: Context) {
    private val context = context.applicationContext

    @Suppress("DEPRECATION")
    fun validate(file: File, release: UpdateRelease, checkCancelled: () -> Unit = {}) {
        verifyUpdatePayload(file, release, checkCancelled)
        checkCancelled()
        val manager = context.packageManager
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val archive = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw ApkValidationException(ApkValidationFailure.Package)
        if (archive.packageName != context.packageName) throw ApkValidationException(ApkValidationFailure.Package)
        if (archive.longVersionCode <= installed.longVersionCode || archive.versionName != release.versionName)
            throw ApkValidationException(ApkValidationFailure.Version)
        if ((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT)
            throw ApkValidationException(ApkValidationFailure.Platform)
        val current = installed.signingInfo ?: throw ApkValidationException(ApkValidationFailure.Signature)
        val incoming = archive.signingInfo ?: throw ApkValidationException(ApkValidationFailure.Signature)
        val currentSigners = current.apkContentsSigners.orEmpty().toSet()
        val compatible = if (current.hasMultipleSigners() || incoming.hasMultipleSigners()) {
            currentSigners.isNotEmpty() && currentSigners == incoming.apkContentsSigners.orEmpty().toSet()
        } else {
            val history = incoming.signingCertificateHistory.orEmpty().toSet()
            currentSigners.isNotEmpty() && history.containsAll(currentSigners)
        }
        if (!compatible) throw ApkValidationException(ApkValidationFailure.Signature)
        checkCancelled()
    }
}

/** Size and hash bind the parsed APK to the release metadata, independent of device clocks. */
internal fun verifyUpdatePayload(file: File, release: UpdateRelease, checkCancelled: () -> Unit = {}) {
    if (release.apkSize !in 1..MAX_UPDATE_APK_BYTES || !file.isFile || file.length() != release.apkSize ||
        !release.sha256.matches(Regex("[0-9a-f]{64}"))) throw ApkValidationException(ApkValidationFailure.Payload)
    val digest = MessageDigest.getInstance("SHA-256")
    var bytes = 0L
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            checkCancelled()
            val count = input.read(buffer)
            if (count < 0) break
            bytes += count
            if (bytes > release.apkSize) throw ApkValidationException(ApkValidationFailure.Payload)
            digest.update(buffer, 0, count)
        }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    if (bytes != release.apkSize || actual != release.sha256) throw ApkValidationException(ApkValidationFailure.Payload)
}
