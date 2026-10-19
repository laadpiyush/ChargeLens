package com.chargelens.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.chargelens.app.data.battery.BatteryRepository
import com.chargelens.app.data.history.SessionRecorder
import com.chargelens.app.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Keeps sampling while the app is in the background: ongoing notification, charge-session
 * recording, and the power-connected trigger for the charging screen. Power and screen broadcasts
 * are registered here at runtime because manifest receivers no longer get them on Android 8+.
 */
@AndroidEntryPoint
class MonitoringService : LifecycleService() {

    @Inject lateinit var repository: BatteryRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var recorder: SessionRecorder
    @Inject lateinit var notifications: Notifications
    @Inject lateinit var launcher: ChargingScreenLauncher

    private var lastNotificationAt = 0L
    private val plugged = MutableStateFlow(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    plugged.value = true
                    launcher.onPowerConnected(repository.state.value.snapshot)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    plugged.value = false
                    launcher.onPowerDisconnected()
                }
                Intent.ACTION_SCREEN_ON -> recorder.screenOn = true
                Intent.ACTION_SCREEN_OFF -> {
                    recorder.screenOn = false
                    launcher.onScreenOff(repository.state.value.snapshot)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!goForeground()) {
            stopSelf()
            return
        }
        _status.value = Status.ACTIVE
        plugged.value = isPluggedIn()
        recorder.screenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        lifecycleScope.launch {
            combine(plugged, settings.flow) { p, s -> Mode.of(p, s) }
                .distinctUntilChanged()
                .collectLatest { mode ->
                    when (mode) {
                        Mode.MONITOR -> monitor()
                        Mode.IDLE -> idle()
                        Mode.STOP -> {
                            // Nothing should run on battery: save the session and go away.
                            recorder.flush()
                            stopSelf()
                        }
                    }
                }
        }
    }

    /** Full monitoring: sample, record sessions, keep the notification live. */
    private suspend fun monitor() {
        _status.value = Status.ACTIVE
        lastNotificationAt = 0L
        repository.state.collect { state ->
            val snapshot = state.snapshot ?: return@collect
            recorder.onSnapshot(snapshot)
            val s = settings.settings.value
            val now = SystemClock.elapsedRealtime()
            if (now - lastNotificationAt >= s.notification.updateIntervalSeconds * 1000L) {
                notifications.updateMonitoring(notifications.monitoringNotification(snapshot, s))
                lastNotificationAt = now
            }
        }
    }

    /**
     * On battery with "only while charging": no sampling or recording. The service stays alive
     * only so its runtime receivers can react to the charger being plugged in (manifest receivers
     * can't get ACTION_POWER_CONNECTED on Android 8+). An idle receiver costs no CPU.
     */
    private suspend fun idle() {
        _status.value = Status.IDLE
        recorder.flush()
        notifications.updateMonitoring(notifications.idleNotification())
        awaitCancellation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            lifecycleScope.launch {
                settings.update { it.copy(monitoringEnabled = false) }
                stopSelf()
            }
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (_status.value != Status.OFF) {
            runCatching { unregisterReceiver(receiver) }
            // Save the open session before the process may go away.
            runBlocking { withContext(NonCancellable) { recorder.flush() } }
        }
        _status.value = Status.OFF
        super.onDestroy()
    }

    private fun isPluggedIn(): Boolean {
        val sticky = ContextCompat.registerReceiver(
            this, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    private fun goForeground(): Boolean = try {
        val s = settings.settings.value
        val notification = if (s.monitorOnlyWhileCharging && !isPluggedIn()) notifications.idleNotification()
        else notifications.monitoringNotification(repository.state.value.snapshot, s)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.ID_MONITORING, notification, type)
        true
    } catch (e: Exception) {
        Log.w(TAG, "startForeground not allowed", e)
        false
    }

    enum class Status { OFF, IDLE, ACTIVE }

    private enum class Mode {
        MONITOR, IDLE, STOP;

        companion object {
            fun of(plugged: Boolean, s: com.chargelens.app.data.settings.AppSettings): Mode = when {
                !s.shouldMonitor(plugged) -> STOP
                plugged || !s.monitorOnlyWhileCharging -> MONITOR
                else -> IDLE
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.chargelens.app.action.STOP_MONITORING"
        private const val TAG = "MonitoringService"
        private val _status = MutableStateFlow(Status.OFF)
        /** OFF = service stopped, IDLE = waiting for a charger, ACTIVE = sampling. */
        val status: StateFlow<Status> = _status.asStateFlow()

    }
}
