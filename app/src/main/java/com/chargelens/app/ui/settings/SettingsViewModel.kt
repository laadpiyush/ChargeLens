package com.chargelens.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chargelens.app.data.battery.BatteryRepository
import com.chargelens.app.data.history.CsvExporter
import com.chargelens.app.data.history.SessionDao
import com.chargelens.app.data.settings.AppSettings
import com.chargelens.app.data.settings.SettingsRepository
import com.chargelens.app.service.MonitoringController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val battery: BatteryRepository,
    private val exporter: CsvExporter,
    private val dao: SessionDao,
    private val monitoring: MonitoringController,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
    val detectedDesignCapacity: Int? get() = battery.designCapacityMah

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { repository.update(transform) }

    fun setMonitoring(enabled: Boolean) = monitoring.setEnabled(enabled)

    fun resetCapacity() = viewModelScope.launch {
        battery.resetCapacityEstimate()
        _messages.emit("Capacity estimate reset. It will be re-learned over the next charges.")
    }

    fun export(uri: Uri) = viewModelScope.launch {
        val msg = runCatching { exporter.export(uri) }
            .fold({ "Exported $it rows" }, { "Export failed: ${it.message}" })
        _messages.emit(msg)
    }

    fun clearHistory() = viewModelScope.launch {
        dao.clearAll()
        _messages.emit("History cleared")
    }
}
