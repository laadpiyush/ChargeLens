package com.chargelens.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargelens.app.data.settings.SettingsRepository
import com.chargelens.app.ui.charging.ChargingScreen
import com.chargelens.app.ui.details.DetailsStateSource
import com.chargelens.app.ui.details.DetailsUiState
import com.chargelens.app.ui.theme.ChargeLensTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Optional overlay mode: shows the charging screen in a SYSTEM_ALERT_WINDOW over other apps. */
@Singleton
class OverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateSource: DetailsStateSource,
    private val settings: SettingsRepository,
    private val monitoring: dagger.Lazy<MonitoringController>,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: ComposeHostOwner? = null
    private var params: WindowManager.LayoutParams? = null

    fun show() {
        if (view != null || !Settings.canDrawOverlays(context)) return
        val host = ComposeHostOwner().also { it.create() }
        val composeView = ComposeView(context).apply {
            setContent {
                val state by stateSource.uiState.collectAsStateWithLifecycle(DetailsUiState())
                val all by settings.settings.collectAsStateWithLifecycle()
                ChargeLensTheme(design = all.design, forceBlack = all.chargingScreen.amoled) {
                    ChargingScreen(
                        state = state,
                        prefs = all.chargingScreen,
                        onClose = ::hide,
                        onToggleMonitoring = { monitoring.get().setEnabled(!state.monitoringActive) },
                        onDimChange = ::setDimmed,
                        enableDetailSheets = false,
                    )
                }
            }
        }
        host.attachTo(composeView)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        )
        try {
            windowManager.addView(composeView, lp)
        } catch (e: Exception) {
            host.destroy()
            return
        }
        host.resume()
        view = composeView
        owner = host
        params = lp
        setDimmed(false)
    }

    fun hide() {
        val v = view ?: return
        owner?.pause()
        runCatching { windowManager.removeView(v) }
        owner?.destroy()
        view = null
        owner = null
        params = null
    }

    private fun setDimmed(dimmed: Boolean) {
        val v = view ?: return
        val lp = params ?: return
        val amoled = settings.settings.value.chargingScreen.amoled
        lp.screenBrightness = when {
            dimmed -> 0.02f
            amoled -> 0.3f
            else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        runCatching { windowManager.updateViewLayout(v, lp) }
    }
}
