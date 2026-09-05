package com.riftdeck.core.launcher

import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Android owns the default-home choice; it is never stored as an app preference. */
class HomeLauncher(private val context: Context) {
    private val roles = context.getSystemService(RoleManager::class.java)

    fun isDefault(): Boolean = roles?.isRoleHeld(RoleManager.ROLE_HOME) == true

    fun removeOtherLauncherTasks(currentTaskId: Int, component: ComponentName) {
        // Android creates a separate HOME task when the role changes from an ordinary app.
        context.getSystemService(ActivityManager::class.java).appTasks.forEach { task ->
            val info = task.taskInfo
            if (info.taskId != currentTaskId && info.baseIntent.component == component) {
                task.finishAndRemoveTask()
            }
        }
    }

    fun requestIntent(): Intent? =
        if (roles?.isRoleAvailable(RoleManager.ROLE_HOME) == true && !isDefault()) {
            roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else null

    fun openSettings(home: Boolean): Boolean {
        val actions = if (home) listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_SETTINGS)
            else listOf(Settings.ACTION_SETTINGS)
        return actions.any { action ->
            try {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }
}
