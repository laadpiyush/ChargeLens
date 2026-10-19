package com.chargelens.app.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chargelens.app.domain.model.BatterySnapshot
import com.chargelens.app.domain.model.Metric
import com.chargelens.app.domain.model.MetricStats
import com.chargelens.app.domain.model.TemperatureUnit
import com.chargelens.app.ui.components.LineChart
import com.chargelens.app.ui.theme.LocalExtras
import com.chargelens.app.util.Format
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricDetailSheet(metric: Metric, state: DetailsUiState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extras = LocalExtras.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = extras.card) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            val model = buildTileModel(metric, state, extras)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(model.icon, null, tint = model.iconTint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(metric.label, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.5.sp, color = extras.muted)
            }
            Spacer(Modifier.height(8.dp))
            Text(model.value, fontFamily = extras.numberFont, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = model.valueColor ?: MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(16.dp))

            if (metric.graphable) {
                val unit = state.temperatureUnit
                LineChart(
                    values = metric.displaySeries(state.battery.series, unit),
                    color = model.sparkColor,
                    formatLabel = { formatAxis(metric, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text("Last ${state.battery.series[metric]?.size ?: 0} samples", style = MaterialTheme.typography.bodySmall, color = extras.muted)
                Spacer(Modifier.height(16.dp))
                val stats = state.battery.stats[metric]?.let { convertStats(metric, it, unit) }
                StatsRow(metric, stats)
                Spacer(Modifier.height(8.dp))
                val since = state.battery.sessionStartedAt.takeIf { it > 0 }
                    ?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) }
                Text(
                    "Session stats since ${since ?: "—"} (reset when the charger is plugged in or removed)",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.muted,
                )
            } else {
                InfoRows(metric, state.battery.snapshot)
            }
        }
    }
}

@Composable
private fun StatsRow(metric: Metric, stats: MetricStats?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("MIN" to stats?.min, "AVG" to stats?.avg, "MAX" to stats?.max).forEach { (label, v) ->
            Surface(shape = RoundedCornerShape(16.dp), color = LocalExtras.current.tile, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, color = LocalExtras.current.muted)
                    Text(v?.let { formatAxis(metric, it) } ?: NOT_AVAILABLE, fontFamily = LocalExtras.current.numberFont, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun InfoRows(metric: Metric, s: BatterySnapshot?) {
    val rows: List<Pair<String, String>> = when (metric) {
        Metric.HEALTH -> listOf(
            "Estimated health" to (s?.healthPercent?.let { "${Format.oneDecimal(it)}%" } ?: NOT_AVAILABLE),
            "Estimated full capacity" to (s?.maxCapacityMah?.let { "$it mAh" } ?: "Learning…"),
            "Design capacity" to (s?.designCapacityMah?.let { "$it mAh" } ?: "Unknown — set it in Settings"),
            "Reported condition" to (s?.condition?.label ?: NOT_AVAILABLE),
        )
        Metric.PLUGGED -> listOf(
            "Power source" to (s?.plugType?.label ?: NOT_AVAILABLE),
            "Status" to (s?.status?.label ?: NOT_AVAILABLE),
            "Charge speed" to (s?.speed?.label?.lowercase()?.replaceFirstChar(Char::uppercase) ?: NOT_AVAILABLE),
        )
        Metric.MAX_CAPACITY -> listOf(
            "Estimated full capacity" to (s?.maxCapacityMah?.let { "$it mAh" } ?: "Learning…"),
            "Charge remaining" to (s?.remainingMah?.let { "$it mAh" } ?: NOT_AVAILABLE),
            "Design capacity" to (s?.designCapacityMah?.let { "$it mAh" } ?: "Unknown"),
        )
        Metric.CHARGE_STATUS -> listOfNotNull(
            "Status" to (s?.status?.label ?: NOT_AVAILABLE),
            s?.timeToFullMinutes?.let { "Time to full" to Format.duration(it) },
            s?.timeRemainingMinutes?.let { "Time remaining" to Format.duration(it) },
            "Technology" to (s?.technology ?: NOT_AVAILABLE),
            "Level" to (s?.level?.let { "$it%" } ?: NOT_AVAILABLE),
        )
        else -> emptyList()
    }
    val note = when (metric) {
        Metric.HEALTH -> "Health = estimated full capacity ÷ design capacity, capped at 100%. The estimate improves over a few charge cycles."
        Metric.MAX_CAPACITY -> "Estimated from the fuel gauge's charge counter divided by the reported percentage, smoothed over time."
        else -> null
    }
    Column {
        rows.forEachIndexed { i, (k, v) ->
            if (i > 0) HorizontalDivider(color = LocalExtras.current.track)
            Row(Modifier.padding(vertical = 12.dp)) {
                Text(k, color = LocalExtras.current.muted, modifier = Modifier.weight(1f))
                Text(v, fontWeight = FontWeight.Medium)
            }
        }
        if (note != null) {
            Spacer(Modifier.height(12.dp))
            Text(note, style = MaterialTheme.typography.bodySmall, color = LocalExtras.current.muted)
        }
    }
}

private fun convertStats(metric: Metric, stats: MetricStats, unit: TemperatureUnit): MetricStats =
    if (metric == Metric.TEMPERATURE && unit == TemperatureUnit.FAHRENHEIT) {
        stats.copy(
            min = Format.temperatureValue(stats.min, unit),
            max = Format.temperatureValue(stats.max, unit),
            avg = Format.temperatureValue(stats.avg, unit),
        )
    } else stats

private fun formatAxis(metric: Metric, v: Float): String = when (metric) {
    Metric.VOLTAGE -> "${v.toInt()} mV"
    Metric.CURRENT -> "${Format.signed(v.toInt())} mA"
    Metric.WATTAGE -> String.format(Locale.US, "%.1f W", v)
    Metric.TEMPERATURE -> String.format(Locale.US, "%.1f°", v)
    else -> v.toString()
}
