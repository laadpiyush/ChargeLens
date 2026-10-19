package com.chargelens.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.dreams.DreamService
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargelens.app.data.settings.SettingsRepository
import com.chargelens.app.ui.charging.ScreensaverScreen
import com.chargelens.app.ui.details.DetailsStateSource
import com.chargelens.app.ui.details.DetailsUiState
import com.chargelens.app.ui.theme.ChargeLensTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Screen saver mode: Android shows this while the phone is idle and charging or docked, once the
 * user selects ChargeLens under Settings → Display → Screen saver.
 */
@AndroidEntryPoint
class ChargeLensDreamService : DreamService() {

    @Inject lateinit var stateSource: DetailsStateSource
    @Inject lateinit var settings: SettingsRepository

    private val owner = ComposeHostOwner()

    private val unplugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val cs = settings.settings.value.chargingScreen
        isInteractive = false // gauge only: any touch ends the screen saver
        isFullscreen = true
        isScreenBright = !cs.amoled

        owner.create()
        val view = ComposeView(this).apply {
            setContent {
                val state by stateSource.uiState.collectAsStateWithLifecycle(DetailsUiState())
                val all by settings.settings.collectAsStateWithLifecycle()
                ChargeLensTheme(design = all.design, forceBlack = all.chargingScreen.amoled) {
                    ScreensaverScreen(
                        state = state,
                        prefs = all.chargingScreen,
                        onDimChange = { dimmed -> isScreenBright = !dimmed && !all.chargingScreen.amoled },
                    )
                }
            }
        }
        owner.attachTo(view)
        window?.decorView?.let(owner::attachTo)
        setContentView(view)
        ContextCompat.registerReceiver(
            this, unplugReceiver, IntentFilter(Intent.ACTION_POWER_DISCONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        owner.resume()
    }

    override fun onDreamingStopped() {
        owner.pause()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        runCatching { unregisterReceiver(unplugReceiver) }
        owner.destroy()
        super.onDetachedFromWindow()
    }
}
