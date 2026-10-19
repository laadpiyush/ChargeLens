package com.chargelens.app.ui.charging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chargelens.app.data.settings.AppSettings
import com.chargelens.app.data.settings.SettingsRepository
import com.chargelens.app.service.MonitoringController
import com.chargelens.app.ui.details.DetailsStateSource
import com.chargelens.app.ui.details.DetailsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChargingViewModel @Inject constructor(
    source: DetailsStateSource,
    settingsRepository: SettingsRepository,
    private val monitoring: MonitoringController,
) : ViewModel() {
    val details: StateFlow<DetailsUiState> =
        source.uiState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), DetailsUiState())

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    fun toggleMonitoring() = monitoring.setEnabled(!details.value.monitoringActive)
}
