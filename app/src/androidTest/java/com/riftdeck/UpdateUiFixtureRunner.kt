package com.riftdeck

import android.app.Activity
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.riftdeck.core.update.ReleaseVersion
import com.riftdeck.core.update.UpdateRelease
import com.riftdeck.data.repository.UpdatePreferencesRepository
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

/** Seeds synthetic update metadata for controller UI checks on an isolated emulator only. */
class UpdateUiFixtureRunner : AndroidJUnitRunner() {
    private var mode: String = "available"

    override fun onCreate(arguments: Bundle) {
        mode = arguments.getString("mode") ?: "available"
        super.onCreate(arguments)
    }

    override fun onStart() {
        val result = Bundle()
        try {
            require(mode == "available" || mode == "ready") { "mode must be available or ready" }
            val current = requireNotNull(ReleaseVersion.parse(BuildConfig.VERSION_NAME))
            val next = current.copy(patch = current.patch + 1)
            val bytes = context.assets.open("update-fixtures/matching.apk").use { it.readBytes() }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val tag = "v$next"
            val notes = buildString {
                appendLine("Synthetic update UI fixture — do not install this APK.")
                appendLine("This manifest-only fixture is used to verify the Android installation confirmation screen.")
                repeat(40) { index -> appendLine("${index + 1}. Controller test note: browse, focus, launch, and return.") }
                append("End of the synthetic release notes.")
            }
            val release = UpdateRelease(tag, next.toString(), next, notes,
                "https://github.com/dos1in/RiftDeck/releases/tag/$tag",
                "https://github.com/dos1in/RiftDeck/releases/download/$tag/RiftDeck-$tag.apk",
                bytes.size.toLong(), hash)
            val directory = File(targetContext.filesDir, "updates").apply {
                check(mkdirs() || isDirectory) { "Cannot create update fixture directory" }
            }
            val apk = File(directory, "release-$hash.apk")
            if (mode == "ready") apk.writeBytes(bytes)
            else check(!apk.exists() || apk.delete()) { "Cannot clear the ready fixture" }
            val preferences = UpdatePreferencesRepository(targetContext)
            runBlocking {
                val now = System.currentTimeMillis()
                preferences.markAttempt(now)
                preferences.recordSuccess(now, release)
                preferences.setAutoCheck(true)
                preferences.dismissVersion("")
            }
            result.putString("setup_status", mode)
            result.putString("seed_version", next.toString())
            finish(Activity.RESULT_OK, result)
        } catch (error: Exception) {
            result.putString("setup_status", "error")
            result.putString("setup_error", "${error.javaClass.simpleName}: ${error.message}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
