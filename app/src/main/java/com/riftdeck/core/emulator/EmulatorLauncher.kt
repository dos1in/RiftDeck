package com.riftdeck.core.emulator

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
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
            val retroArch = RetroArchLaunch.supports(config)
            val nativePath = if (retroArch) {
                val source = Uri.parse(game.romUri)
                if (source.scheme != "content" || source.authority != "com.android.externalstorage.documents") {
                    return LaunchResult.RomUnavailable
                }
                RetroArchLaunch.documentPath(DocumentsContract.getDocumentId(source),
                    Environment.getExternalStorageDirectory().absolutePath)
                    ?: return LaunchResult.RomUnavailable
            } else null
            val rom = try {
                if (nativePath != null) withContext(Dispatchers.IO) {
                    val source = Uri.parse(game.romUri)
                    context.contentResolver.openInputStream(source)?.use {
                        if (it.read() < 0) throw java.io.FileNotFoundException("Empty ROM")
                    } ?: throw java.io.FileNotFoundException("ROM unavailable")
                    // RetroArch reads ZIP content itself; keep the original filename for save lookup.
                    PreparedRom(source, game.fileName)
                } else roms.prepare(game)
            }
            catch (_: IllegalArgumentException) {
                // Some SAF providers report a removed document as IllegalArgumentException.
                return LaunchResult.RomUnavailable
            }
            val intent = if (nativePath != null) {
                Intent(Intent.ACTION_MAIN).setComponent(component).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    RetroArchLaunch.extras(nativePath, Environment.getExternalStorageDirectory().absolutePath)
                        .forEach { (key, value) -> putExtra(key, value) }
                }
            } else Intent(config.action).setComponent(component).apply {
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
        } catch (error: SecurityException) {
            if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) android.util.Log.w("Emulator", "ROM access denied", error)
            LaunchResult.RomUnavailable
        } catch (_: java.util.zip.ZipException) {
            LaunchResult.InvalidArchive(ArchiveFailure.Invalid)
        } catch (error: IOException) {
            if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) android.util.Log.w("Emulator", "ROM preparation failed", error)
            LaunchResult.RomUnavailable
        } catch (_: IllegalArgumentException) {
            LaunchResult.InvalidConfiguration
        }
    }
}
