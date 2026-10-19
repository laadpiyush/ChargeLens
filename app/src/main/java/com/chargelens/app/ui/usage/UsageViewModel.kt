package com.chargelens.app.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chargelens.app.data.history.SessionDao
import com.chargelens.app.data.history.SessionEntity
import com.chargelens.app.data.history.SessionType
import com.chargelens.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DrainRate(val percentPerHour: Float?, val mahPerHour: Float?, val durationMs: Long)

data class UsageUiState(
    val current: SessionEntity? = null,
    val past: List<SessionEntity> = emptyList(),
    val monitoring: Boolean = false,
    /** Discharge isn't recorded because monitoring only runs while charging. */
    val chargingOnly: Boolean = false,
)

fun SessionEntity.screenOnRate() = rate(screenOnDropPct, screenOnMah, screenOnMs)
fun SessionEntity.screenOffRate() = rate(screenOffDropPct, screenOffMah, screenOffMs)
fun SessionEntity.overallRate() = rate(screenOnDropPct + screenOffDropPct, screenOnMah + screenOffMah, screenOnMs + screenOffMs)

/** Rates need at least five minutes of data to mean anything. */
private fun rate(pct: Float, mah: Float, ms: Long): DrainRate {
    val hours = ms / 3_600_000f
    return if (ms < 5 * 60_000L) DrainRate(null, null, ms) else DrainRate(pct / hours, mah / hours, ms)
}

@HiltViewModel
class UsageViewModel @Inject constructor(dao: SessionDao, settings: SettingsRepository) : ViewModel() {
    val uiState: StateFlow<UsageUiState> =
        combine(dao.observeSessions(SessionType.DISCHARGE), settings.settings) { list, s ->
            val current = list.firstOrNull { it.endTime == null }
            UsageUiState(
                current = current,
                past = list.filter { it.id != current?.id },
                monitoring = s.monitoringEnabled,
                chargingOnly = s.monitorOnlyWhileCharging,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), UsageUiState())
}
