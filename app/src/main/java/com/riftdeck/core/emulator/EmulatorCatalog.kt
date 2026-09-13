package com.riftdeck.core.emulator

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmulatorCatalog(private val context: Context) {
    suspend fun installedFor(platformId: Long): List<EmulatorConfig> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val homePackages = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        ).mapTo(mutableSetOf()) { it.activityInfo.packageName }
        val candidates = mutableListOf<EmulatorConfig>()
        for (mime in listOf("application/x-gba-rom", "application/octet-stream")) {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://${context.packageName}.roms/game.gba"), mime)
            @Suppress("DEPRECATION")
            val matches = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            matches.forEach { match ->
                val activity = match.activityInfo
                if (activity.packageName != context.packageName && activity.packageName !in homePackages &&
                    activity.exported && activity.enabled && activity.applicationInfo.enabled) {
                    candidates.add(EmulatorConfig(platformId, activity.packageName, activity.name,
                        match.loadLabel(pm).toString(), mimeType = mime))
                }
            }
        }
        // Prefer explicit game-entry activities over generic file handlers from the same app.
        GbaEmulatorProfiles.forPlatform(platformId).forEach { profile ->
            val component = android.content.ComponentName(profile.packageName, profile.activityName)
            try {
                @Suppress("DEPRECATION")
                val activity = pm.getActivityInfo(component, 0)
                if (activity.exported && activity.enabled && activity.applicationInfo.enabled) {
                    candidates.removeAll { it.packageName == profile.packageName }
                    candidates.add(profile)
                }
            } catch (_: PackageManager.NameNotFoundException) { /* Optional external emulator. */ }
        }
        candidates.distinctBy { it.packageName to it.activityName }.sortedBy { it.displayName.lowercase() }
    }
}
