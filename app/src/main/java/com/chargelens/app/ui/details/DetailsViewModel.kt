package com.chargelens.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chargelens.app.service.MonitoringController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    source: DetailsStateSource,
    private val monitoring: MonitoringController,
) : ViewModel() {

    val uiState: StateFlow<DetailsUiState> =
        source.uiState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), DetailsUiState())

    fun toggleMonitoring() = monitoring.setEnabled(!uiState.value.monitoringActive)
}
