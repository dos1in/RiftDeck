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
        val candidates = mutableListOf<EmulatorConfig>()
        for (mime in listOf("application/x-gba-rom", "application/octet-stream")) {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://${context.packageName}.roms/game.gba"), mime)
            @Suppress("DEPRECATION")
            val matches = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            matches.forEach { match ->
                val activity = match.activityInfo
                if (activity.packageName != context.packageName && activity.exported && activity.enabled && activity.applicationInfo.enabled) {
                    candidates.add(EmulatorConfig(platformId, activity.packageName, activity.name,
                        match.loadLabel(pm).toString(), mimeType = mime))
                }
            }
        }
        // GBA.emu accepts ACTION_VIEW on its main activity; discover its actual component.
        pm.getLaunchIntentForPackage("com.explusalpha.GbaEmu")?.component?.let { component ->
            candidates.add(EmulatorConfig(platformId, component.packageName, component.className, "GBA.emu"))
        }
        candidates.distinctBy { it.packageName to it.activityName }.sortedBy { it.displayName.lowercase() }
    }
}
