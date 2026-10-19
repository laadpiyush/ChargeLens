package com.chargelens.app.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.chargelens.app.data.settings.SettingsRepository
import com.chargelens.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Starts/stops [MonitoringService] and persists the user's choice. */
@Singleton
class MonitoringController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {

    fun setEnabled(enabled: Boolean) {
        scope.launch {
            settings.update { it.copy(monitoringEnabled = enabled) }
            if (enabled) sync() else stop()
        }
    }

    /**
     * Starts or stops the service to match settings and charger state. Safe to call repeatedly;
     * call it whenever the app comes to the foreground or the plug state changes while visible.
     */
    suspend fun sync() {
        val should = settings.load().shouldMonitor(isPluggedIn())
        val off = MonitoringService.status.value == MonitoringService.Status.OFF
        if (should && off) start() else if (!should && !off) stop()
    }

    private fun isPluggedIn(): Boolean {
        val sticky = ContextCompat.registerReceiver(
            context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    fun start(): Boolean = try {
        ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java))
        true
    } catch (e: Exception) {
        // ForegroundServiceStartNotAllowedException when started from the background on 12+.
        Log.w(TAG, "Could not start monitoring service", e)
        false
    }

    fun stop() {
        context.stopService(Intent(context, MonitoringService::class.java))
    }

    private companion object {
        const val TAG = "MonitoringController"
    }
}
