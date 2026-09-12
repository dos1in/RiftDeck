package com.riftdeck.core.media

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Activity-owned observation; never changes brightness, timeout, or wake locks. */
class PreviewScreenMonitor(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val mutableActive = MutableStateFlow(false)
    val active = mutableActive.asStateFlow()
    private var resumed = false
    private var focused = true
    private var timeout = 30000L
    private var lastInteraction = 0L
    private var settingsJob: Job? = null
    private val pause = Runnable { mutableActive.value = false }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { readTimeout() }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) suspendPreview() else userActivity()
        }
    }

    fun resume() {
        if (resumed) return
        resumed = true
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT), false, observer)
        userActivity()
        readTimeout()
    }

    fun pause() {
        if (!resumed) return
        resumed = false
        settingsJob?.cancel()
        context.unregisterReceiver(receiver)
        context.contentResolver.unregisterContentObserver(observer)
        suspendPreview()
    }

    fun destroy() { pause(); scope.cancel() }

    fun windowFocusChanged(hasFocus: Boolean) {
        focused = hasFocus
        if (hasFocus) userActivity() else suspendPreview()
    }

    fun userActivity() {
        if (!resumed || !focused) return
        lastInteraction = SystemClock.uptimeMillis()
        schedule()
    }

    private fun readTimeout() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            timeout = withContext(Dispatchers.IO) {
                Settings.System.getLong(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30000L)
            }
            if (resumed) schedule()
        }
    }

    private fun schedule() {
        handler.removeCallbacks(pause)
        val remaining = previewPauseDelay(timeout) - (SystemClock.uptimeMillis() - lastInteraction)
        mutableActive.value = resumed && focused && power.isInteractive && !keyguard.isKeyguardLocked && remaining > 0
        if (mutableActive.value) handler.postDelayed(pause, remaining)
    }

    private fun suspendPreview() { handler.removeCallbacks(pause); mutableActive.value = false }
}
