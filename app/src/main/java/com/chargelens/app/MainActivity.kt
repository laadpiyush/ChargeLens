package com.chargelens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chargelens.app.data.battery.BatteryRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.chargelens.app.data.settings.SettingsRepository
import com.chargelens.app.service.MonitoringController
import com.chargelens.app.ui.navigation.AppRoot
import com.chargelens.app.ui.theme.ChargeLensTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var monitoring: MonitoringController
    @Inject lateinit var repository: BatteryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // While the app is visible (so starting a foreground service is allowed), keep the
        // service in step with settings and the charger: this is what starts monitoring when
        // ChargeLens is opened on the charger with "wait for charger in background" off.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repository.state.map { it.snapshot?.isPlugged }.distinctUntilChanged(),
                    settings.flow,
                ) { _, _ -> }.collect { monitoring.sync() }
            }
        }
        setContent {
            val s by settings.settings.collectAsStateWithLifecycle()
            ChargeLensTheme(design = s.design) { AppRoot() }
        }
    }


}
