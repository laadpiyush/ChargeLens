package com.chargelens.app.ui.charging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargelens.app.service.Notifications
import com.chargelens.app.ui.theme.ChargeLensTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Always-on-display style view shown over the lock screen when the screen turns off while
 * charging. Third-party apps cannot draw on the system AOD, so this keeps the screen on at very
 * low brightness instead. Tap to go to the lock screen; press power to turn it off; it closes by
 * itself when unplugged.
 */
@AndroidEntryPoint
class AodActivity : ComponentActivity() {

    @Inject lateinit var notifications: Notifications

    private val viewModel: ChargingViewModel by viewModels()
    private var receiverRegistered = false

    /** True once resumed with the display on. Launching while asleep briefly stops the activity
     *  before the screen wakes; that stop must not be mistaken for the user leaving. */
    private var shownToUser = false

    private val unplugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = AOD_BRIGHTNESS }
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        notifications.cancelChargingPrompt()

        if (!isPluggedIn()) {
            finish()
            return
        }
        ContextCompat.registerReceiver(
            this, unplugReceiver, IntentFilter(Intent.ACTION_POWER_DISCONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true

        setContent {
            val state by viewModel.details.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            ChargeLensTheme(design = settings.design, forceBlack = true) {
                AodScreen(state = state, prefs = settings.chargingScreen, onDismiss = ::finish)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (getSystemService(PowerManager::class.java)?.isInteractive == true) shownToUser = true
        visible = true
    }

    override fun onPause() {
        visible = false
        if (shownToUser) lastVisibleAt = SystemClock.elapsedRealtime()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Screen turned off (power button) or another app came up after being seen: done.
        // The next screen-off while charging launches a fresh one.
        if (shownToUser && !isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(unplugReceiver)
        super.onDestroy()
    }

    private fun isPluggedIn(): Boolean {
        val sticky = ContextCompat.registerReceiver(
            this, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    companion object {
        private const val AOD_BRIGHTNESS = 0.04f

        @Volatile var visible = false
            private set

        /** When the view was last on screen; used to tell "power pressed on AOD" from a normal screen-off. */
        @Volatile var lastVisibleAt = 0L
            private set

        fun intent(context: Context): Intent =
            Intent(context, AodActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
