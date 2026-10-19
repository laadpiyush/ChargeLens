package com.chargelens.app.ui.charging

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import com.chargelens.app.data.settings.SettingsRepository
import com.chargelens.app.service.Notifications
import com.chargelens.app.ui.theme.ChargeLensTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full-screen charging display. Shows over the lock screen and turns the screen on; finishes when
 * the charger is removed (unless opened as a preview from the menu).
 */
@AndroidEntryPoint
class ChargingActivity : ComponentActivity() {

    @Inject lateinit var notifications: Notifications
    @Inject lateinit var settingsRepository: SettingsRepository

    private val viewModel: ChargingViewModel by viewModels()
    private var preview = false
    private var receiverRegistered = false

    private val unplugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!preview) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        hideSystemBars()
        notifications.cancelChargingPrompt()

        preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        if (!preview && !isPluggedIn()) {
            // Stale launch (charger already removed by the time the notification fired).
            finish()
            return
        }

        lifecycleScope.launch {
            if (settingsRepository.load().chargingScreen.dismissKeyguard) {
                getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this@ChargingActivity, null)
            }
        }
        ContextCompat.registerReceiver(
            this, unplugReceiver, IntentFilter(Intent.ACTION_POWER_DISCONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true

        setContent {
            val state by viewModel.details.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            ChargeLensTheme(design = settings.design, forceBlack = settings.chargingScreen.amoled) {
                ChargingScreen(
                    state = state,
                    prefs = settings.chargingScreen,
                    onClose = ::finish,
                    onToggleMonitoring = viewModel::toggleMonitoring,
                    onDimChange = ::applyBrightness,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        notifications.cancelChargingPrompt()
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(unplugReceiver)
        super.onDestroy()
    }

    private fun applyBrightness(dimmed: Boolean) {
        val amoled = viewModel.settings.value.chargingScreen.amoled
        window.attributes = window.attributes.apply {
            screenBrightness = when {
                dimmed -> 0.02f
                amoled -> 0.3f
                else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun isPluggedIn(): Boolean {
        val sticky = ContextCompat.registerReceiver(
            this, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    companion object {
        private const val EXTRA_PREVIEW = "preview"

        fun intent(context: Context, preview: Boolean = false): Intent =
            Intent(context, ChargingActivity::class.java)
                .putExtra(EXTRA_PREVIEW, preview)
                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
