package com.chargelens.app.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.media.AudioManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.chargelens.app.data.settings.ChargingScreenMode
import com.chargelens.app.data.settings.SettingsRepository
import com.chargelens.app.domain.model.BatterySnapshot
import com.chargelens.app.ui.charging.AodActivity
import com.chargelens.app.ui.charging.ChargingActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides how to show the charging screen when power is connected:
 * 1. Charging display activity: started directly when the phone is in use and we may draw
 *    over apps (which exempts us from background-activity-start limits), otherwise via a
 *    full-screen-intent notification.
 * 2. Screen saver: nothing to do, Android's dream manager shows [ChargeLensDreamService].
 * 3. Overlay: a window over other apps, if the permission is granted.
 */
@Singleton
class ChargingScreenLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val notifications: Notifications,
    private val overlay: OverlayController,
) {
    fun onPowerConnected(snapshot: BatterySnapshot?) {
        val cs = settings.settings.value.chargingScreen
        if (isInCall()) return
        // Plugged in with the screen off: go straight to the always-on view.
        if (cs.alwaysOnWhileCharging && !isInteractive()) {
            launch(AodActivity.intent(context), snapshot)
            return
        }
        if (!cs.enabled) return
        if (isAppInForeground()) return
        when (cs.mode) {
            ChargingScreenMode.ACTIVITY -> launch(ChargingActivity.intent(context), snapshot)
            ChargingScreenMode.OVERLAY ->
                if (Settings.canDrawOverlays(context)) overlay.show() else launch(ChargingActivity.intent(context), snapshot)
            ChargingScreenMode.SCREENSAVER -> Unit
        }
    }

    /**
     * Screen turned off: if charging, show the always-on view. Skipped when the user just turned
     * the always-on view itself off with the power button, so it does not bounce straight back.
     */
    fun onScreenOff(snapshot: BatterySnapshot?) {
        val cs = settings.settings.value.chargingScreen
        if (!cs.alwaysOnWhileCharging || !isPluggedIn() || isInCall()) return
        if (SystemClock.elapsedRealtime() - AodActivity.lastVisibleAt < POWER_OFF_GRACE_MS) return
        // Give the keyguard a moment to engage so the full-screen intent launches the activity.
        handler.postDelayed({ if (!isInteractive() && isPluggedIn()) launch(AodActivity.intent(context), snapshot) }, 400)
    }

    fun onPowerDisconnected() {
        notifications.cancelChargingPrompt()
        overlay.hide()
        // ChargingActivity and the dream listen for POWER_DISCONNECTED themselves.
    }

    private fun launch(target: Intent, snapshot: BatterySnapshot?) {
        val canOverlay = Settings.canDrawOverlays(context)
        if (isInteractive() && canOverlay && startDirect(target)) return
        if (notifications.canUseFullScreenIntent() || !canOverlay) {
            notifications.showChargingPrompt(snapshot, target)
        } else if (!startDirect(target)) {
            notifications.showChargingPrompt(snapshot, target)
        }
    }

    private fun isInteractive(): Boolean = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    private fun isPluggedIn(): Boolean {
        val sticky = ContextCompat.registerReceiver(
            context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    private fun startDirect(target: Intent): Boolean = try {
        context.startActivity(Intent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Log.w(TAG, "Direct start of charging screen failed", e)
        false
    }

    private fun isInCall(): Boolean {
        val mode = context.getSystemService(AudioManager::class.java)?.mode ?: return false
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE
    }

    /** True while any ChargeLens activity (main app or charging screen) is visible. */
    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private val handler = Handler(Looper.getMainLooper())

    private companion object {
        const val TAG = "ChargingScreenLauncher"
        const val POWER_OFF_GRACE_MS = 2_000L
    }
}
