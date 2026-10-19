package com.chargelens.app.ui.details

import com.chargelens.app.data.battery.BatteryRepository
import com.chargelens.app.data.settings.DesignSettings
import com.chargelens.app.data.settings.SettingsRepository
import com.chargelens.app.domain.model.LiveBatteryState
import com.chargelens.app.domain.model.TemperatureUnit
import com.chargelens.app.service.MonitoringService
import com.chargelens.app.ui.components.MonitoringState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class DetailsUiState(
    val battery: LiveBatteryState = LiveBatteryState.Empty,
    val monitoring: MonitoringState = MonitoringState.OFF,
    val design: DesignSettings = DesignSettings(),
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
) {
    /** Monitoring is switched on (whether or not it is sampling right now). */
    val monitoringActive: Boolean get() = monitoring != MonitoringState.OFF
}

/**
 * Builds [DetailsUiState] for every host of the Details screen: the main app, the charging
 * activity, the screen saver and the overlay (the last two cannot use Hilt ViewModels).
 */
@Singleton
class DetailsStateSource @Inject constructor(
    repository: BatteryRepository,
    settings: SettingsRepository,
) {
    val uiState: Flow<DetailsUiState> = combine(
        repository.state,
        settings.settings,
        MonitoringService.status,
    ) { battery, s, status ->
        DetailsUiState(
            battery = battery,
            monitoring = when {
                status == MonitoringService.Status.ACTIVE -> MonitoringState.ACTIVE
                status == MonitoringService.Status.IDLE -> MonitoringState.WAITING_IN_BACKGROUND
                !s.monitoringEnabled -> MonitoringState.OFF
                // Enabled but the service isn't running: about to start (plugged in) or
                // deliberately not running on battery.
                battery.snapshot?.isPlugged == true -> MonitoringState.ACTIVE
                else -> MonitoringState.STARTS_WHEN_OPENED
            },
            design = s.design,
            temperatureUnit = s.temperatureUnit,
        )
    }
}
