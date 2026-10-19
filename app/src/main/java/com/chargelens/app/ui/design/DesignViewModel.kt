package com.chargelens.app.ui.design

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chargelens.app.data.settings.DesignSettings
import com.chargelens.app.data.settings.SettingsRepository
import com.chargelens.app.domain.model.Metric
import com.chargelens.app.ui.details.DetailsStateSource
import com.chargelens.app.ui.details.DetailsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DesignViewModel @Inject constructor(
    source: DetailsStateSource,
    private val settings: SettingsRepository,
) : ViewModel() {

    val preview: StateFlow<DetailsUiState> =
        source.uiState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), DetailsUiState())

    private fun edit(transform: (DesignSettings) -> DesignSettings) = viewModelScope.launch {
        settings.update { it.copy(design = transform(it.design)) }
    }

    fun setAccent(argb: Long) = edit { it.copy(accentArgb = argb) }
    fun setThickness(dp: Float) = edit { it.copy(gaugeThicknessDp = dp) }
    fun setMono(mono: Boolean) = edit { it.copy(monoFont = mono) }
    fun setAmoled(on: Boolean) = edit { it.copy(amoledBlack = on) }
    fun reset() = edit { DesignSettings() }

    fun setTileVisible(metric: Metric, visible: Boolean) = edit {
        it.copy(hiddenTiles = if (visible) it.hiddenTiles - metric else it.hiddenTiles + metric)
    }

    fun moveTile(metric: Metric, delta: Int) = edit {
        val list = it.tileOrder.toMutableList()
        val from = list.indexOf(metric)
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return@edit it
        list.add(to, list.removeAt(from))
        it.copy(tileOrder = list)
    }
}
