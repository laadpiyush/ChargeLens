package com.chargelens.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargelens.app.data.history.SessionEntity
import com.chargelens.app.ui.components.LineChart
import com.chargelens.app.ui.theme.LocalExtras
import com.chargelens.app.util.Format
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras = LocalExtras.current

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val selected = state.selected
        if (selected == null) {
            item {
                Text(
                    "No charge sessions yet. Sessions are recorded while monitoring is on — plug in a charger to start one.",
                    color = extras.muted,
                    modifier = Modifier.padding(8.dp),
                )
            }
            return@LazyColumn
        }

        item {
            Surface(shape = RoundedCornerShape(24.dp), color = extras.tile) {
                Column(Modifier.padding(16.dp)) {
                    Text("POWER OVER TIME", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, color = extras.muted)
                    Text(dateRange(selected), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    LineChart(
                        values = state.samples.mapNotNull { it.powerMw?.let { mw -> abs(mw) / 1000f } },
                        color = extras.accent,
                        formatLabel = { String.format(Locale.US, "%.1f W", it) },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("AVG", Format.watts(selected.avgPowerMw).removePrefix("+"), Modifier.weight(1f))
                        Stat("PEAK", Format.watts(selected.peakPowerMw).removePrefix("+"), Modifier.weight(1f))
                        Stat("PEAK TEMP", selected.peakTempC?.let { "${Format.oneDecimal(it)}°C" } ?: "N/A", Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Text("SESSIONS", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp, color = extras.accent, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
        }
        items(state.sessions, key = { it.id }) { session ->
            SessionRow(session, selected = session.id == selected.id, onClick = { viewModel.select(session.id) })
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = LocalExtras.current.muted)
        Text(value, fontFamily = LocalExtras.current.numberFont, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SessionRow(s: SessionEntity, selected: Boolean, onClick: () -> Unit) {
    val extras = LocalExtras.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = extras.tile,
        border = if (selected) BorderStroke(1.dp, extras.accent) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(dateRange(s), style = MaterialTheme.typography.bodyLarge)
                Text(
                    buildString {
                        append(Format.durationMs(s.durationMs))
                        s.plugType?.let { append(" · $it") }
                        if (s.endTime == null) append(" · in progress")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.muted,
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text("${s.startPercent.roundToInt()}% → ${s.endPercent.roundToInt()}%", fontWeight = FontWeight.SemiBold, fontFamily = extras.numberFont)
                Text("avg ${Format.oneDecimal(s.avgPowerMw / 1000f)} W · peak ${Format.oneDecimal(s.peakPowerMw / 1000f)} W", style = MaterialTheme.typography.bodySmall, color = extras.muted)
            }
        }
    }
}

private fun dateRange(s: SessionEntity): String {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.startTime))
    val end = s.endTime?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: "now"
    return "$date – $end"
}
