package com.chargelens.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chargelens.app.data.history.SampleEntity
import com.chargelens.app.data.history.SessionDao
import com.chargelens.app.data.history.SessionEntity
import com.chargelens.app.data.history.SessionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HistoryUiState(
    val sessions: List<SessionEntity> = emptyList(),
    val selected: SessionEntity? = null,
    val samples: List<SampleEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(dao: SessionDao) : ViewModel() {

    private val selectedId = MutableStateFlow<Long?>(null)
    private val sessions = dao.observeSessions(SessionType.CHARGE)

    val uiState: StateFlow<HistoryUiState> = combine(sessions, selectedId) { list, id ->
        list to (list.firstOrNull { it.id == id } ?: list.firstOrNull())
    }.flatMapLatest { (list, selected) ->
        val samples = selected?.let { dao.observeSamples(it.id) } ?: flowOf(emptyList())
        samples.map { HistoryUiState(list, selected, it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), HistoryUiState())

    fun select(id: Long) {
        selectedId.value = id
    }
}
