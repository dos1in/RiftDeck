package com.riftdeck.data.sharing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.riftdeck.R
import com.riftdeck.app.LauncherApplication
import com.riftdeck.app.MainActivity
import kotlinx.coroutines.*

/** Started by a visible user action; an ongoing notification exposes an immediate Stop action. */
class LanSharingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository get() = (application as LauncherApplication).sharingRepository
    private val owner = java.util.UUID.randomUUID().toString()
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            scope.launch { repository.setEnabled(false) }
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.lan_notification_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_lan_notification)
            .setContentTitle(getString(R.string.lan_notification_title)).setContentText(getString(R.string.lan_notification_message))
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .addAction(0, getString(R.string.lan_notification_stop), stop).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(NOTIFICATION, notification)
            scope.launch {
                try { repository.start(owner) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { repository.reportServiceError(); stopSelf() }
                if (!repository.state.value.running) stopSelf()
            }
        } catch (_: RuntimeException) {
            repository.reportServiceError()
            scope.launch { repository.setEnabled(false) }
            stopSelf()
        }
        return START_STICKY
    }
    override fun onDestroy() {
        // Close sockets before cancelling the worker scope. Application scope owns the cleanup.
        (application as LauncherApplication).stopSharingSession(owner)
        scope.cancel()
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "lan_sharing"
        private const val NOTIFICATION = 2001
        private const val STOP = "com.riftdeck.STOP_LAN_SHARING"
    }
}
