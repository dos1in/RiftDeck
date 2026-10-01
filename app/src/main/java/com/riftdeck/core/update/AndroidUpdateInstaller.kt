package com.riftdeck.core.update

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

enum class InstallResult { Started, PermissionRequired, Unavailable }

/** Only invoked from an explicit user action, after AppUpdateRepository.validatedApk(). */
class AndroidUpdateInstaller(context: Context) {
    private val context = context.applicationContext
    fun canRequestInstall(): Boolean = try { context.packageManager.canRequestPackageInstalls() }
        catch (_: RuntimeException) { false }

    fun requestPermission(activity: Activity): Boolean = try {
        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")))
        true
    } catch (_: RuntimeException) { false }

    fun install(activity: Activity, file: File): InstallResult {
        if (!canRequestInstall()) return if (requestPermission(activity)) InstallResult.PermissionRequired else InstallResult.Unavailable
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newRawUri("RiftDeck update", uri) })
            InstallResult.Started
        } catch (_: RuntimeException) { InstallResult.Unavailable }
    }
}
