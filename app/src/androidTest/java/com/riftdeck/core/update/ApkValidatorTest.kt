package com.riftdeck.core.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.riftdeck.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Real PackageManager parsing and certificates, without installing/replacing the app under test. */
class ApkValidatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(context.cacheDir, "update-validator-${UUID.randomUUID()}").apply { mkdirs() }
    private val validator = ApkValidator(context)
    private val current = ReleaseVersion.parse(BuildConfig.VERSION_NAME)!!
    private val next = current.copy(patch = current.patch + 1)

    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun matchingSignatureAndNewVersionAreAccepted() {
        val (file, release) = fixture("matching.apk")
        validator.validate(file, release)
    }

    @Test fun anUnrelatedSigningKeyIsRejectedWithSpecificReason() {
        reject("wrong-signature.apk", ApkValidationFailure.Signature)
    }

    @Test fun sameVersionCodeCannotBeInstalledAsAnUpdate() {
        reject("old-version.apk", ApkValidationFailure.Version)
    }

    @Test fun anotherPackageCannotReplaceRiftDeck() {
        reject("wrong-package.apk", ApkValidationFailure.Package)
    }

    @Test fun parsedVersionMustMatchTheReleaseMetadata() {
        val (file, release) = fixture("matching.apk")
        try { validator.validate(file, release.copy(versionName = "99.0.0")); fail("Version mismatch accepted") }
        catch (error: ApkValidationException) { assertEquals(ApkValidationFailure.Version, error.failure) }
    }

    @Test fun alteredPrivateApkIsRejectedBeforePackageParsing() {
        val (file, release) = fixture("matching.apk")
        file.appendBytes(byteArrayOf(1))
        try { validator.validate(file, release); fail("Modified APK accepted") }
        catch (error: ApkValidationException) { assertEquals(ApkValidationFailure.Payload, error.failure) }
    }

    private fun reject(name: String, failure: ApkValidationFailure) {
        val (file, release) = fixture(name)
        try { validator.validate(file, release); fail("Invalid APK accepted: $name") }
        catch (error: ApkValidationException) { assertEquals(failure, error.failure) }
    }

    private fun fixture(name: String): Pair<File, UpdateRelease> {
        val file = File(directory, name)
        InstrumentationRegistry.getInstrumentation().context.assets.open("update-fixtures/$name").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        return file to UpdateRelease("v$next", next.toString(), next, "", "https://github.com/dos1in/RiftDeck/releases",
            "https://github.com/dos1in/RiftDeck/releases/download/v$next/$name", file.length(), hash)
    }
}
