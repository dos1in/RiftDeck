package com.riftdeck.core.emulator

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.riftdeck.core.model.Game
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface EmulatorLauncher {
    suspend fun launch(config: EmulatorConfig, game: Game): LaunchResult
}

class AndroidEmulatorLauncher(
    private val context: Context,
    private val roms: RomLaunchPreparer,
) : EmulatorLauncher {
    override suspend fun launch(config: EmulatorConfig, game: Game): LaunchResult {
        if (config.platformId != game.platformId || config.packageName.isBlank() || config.activityName.isBlank()) {
            return LaunchResult.InvalidConfiguration
        }
        return try {
            val component = ComponentName(config.packageName, config.activityName)
            val installed = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val info = context.packageManager.getActivityInfo(component, 0)
                    info.enabled && info.exported && info.applicationInfo.enabled
                } catch (_: PackageManager.NameNotFoundException) { false }
            }
            if (!installed) return LaunchResult.NotInstalled
            val rom = try { roms.prepare(game) }
            catch (_: IllegalArgumentException) {
                // Some SAF providers report a removed document as IllegalArgumentException.
                return LaunchResult.RomUnavailable
            }
            val intent = Intent(config.action).setComponent(component).apply {
                setDataAndType(rom.uri, config.mimeType)
                clipData = ClipData.newRawUri(rom.name, rom.uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                config.extras.forEach { (key, value) -> putExtra(key, value) }
            }
            withContext(Dispatchers.Main.immediate) { context.startActivity(intent) }
            LaunchResult.Started
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ArchiveException) {
            LaunchResult.InvalidArchive(error.reason)
        } catch (_: ActivityNotFoundException) {
            LaunchResult.NotInstalled
        } catch (_: SecurityException) {
            LaunchResult.RomUnavailable
        } catch (_: java.util.zip.ZipException) {
            LaunchResult.InvalidArchive(ArchiveFailure.Invalid)
        } catch (_: IOException) {
            LaunchResult.RomUnavailable
        } catch (_: IllegalArgumentException) {
            LaunchResult.InvalidConfiguration
        }
    }
}
