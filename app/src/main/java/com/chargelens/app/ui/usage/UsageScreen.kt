package com.chargelens.app.ui.usage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargelens.app.data.history.SessionEntity
import com.chargelens.app.ui.theme.LocalExtras
import com.chargelens.app.util.Format
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun UsageScreen(viewModel: UsageViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras = LocalExtras.current

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.monitoring && state.chargingOnly) {
            // Drain isn't recorded in this mode; show only the explanation and past sessions.
            item {
                Text(
                    "Battery drain isn't recorded because monitoring only runs while charging. Turn off \"Only while charging\" in Settings → Monitoring to track usage.",
                    color = extras.muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        if (!state.monitoring) {
            item {
                Text(
                    "Monitoring is paused, so drain is not being recorded. Turn it on from the Details screen.",
                    color = extras.discharge,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        val current = state.current.takeUnless { state.chargingOnly }
        if (!state.chargingOnly) item {
            Text(
                if (current != null) "SINCE UNPLUGGED · ${Format.durationMs(current.durationMs)}" else "CURRENT DISCHARGE",
                style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp, color = extras.accent,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
        }
        if (current == null && !state.chargingOnly) {
            item {
                Text("Unplug the charger to start measuring drain.", color = extras.muted, modifier = Modifier.padding(horizontal = 8.dp))
            }
        } else if (current != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RateCard("SCREEN ON", Icons.Outlined.PhoneAndroid, current.screenOnRate(), Modifier.weight(1f))
                    RateCard("SCREEN OFF", Icons.Outlined.Bedtime, current.screenOffRate(), Modifier.weight(1f))
                }
            }
            item {
                val overall = current.overallRate()
                Surface(shape = RoundedCornerShape(24.dp), color = extras.tile, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Used", color = extras.muted, style = MaterialTheme.typography.bodySmall)
                            Text("${Format.oneDecimal(current.startPercent - current.endPercent)}%", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Average", color = extras.muted, style = MaterialTheme.typography.bodySmall)
                            Text(overall.percentPerHour?.let { "${Format.oneDecimal(it)} %/h" } ?: "—", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Screen on", color = extras.muted, style = MaterialTheme.typography.bodySmall)
                            Text(Format.durationMs(current.screenOnMs), fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        if (state.past.isNotEmpty()) {
            item {
                Text("PAST DISCHARGES", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp, color = extras.accent, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
            }
            items(state.past, key = { it.id }) { PastRow(it) }
        }
    }
}

@Composable
private fun RateCard(title: String, icon: ImageVector, rate: DrainRate, modifier: Modifier = Modifier) {
    val extras = LocalExtras.current
    Surface(shape = RoundedCornerShape(24.dp), color = extras.tile, modifier = modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = extras.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                Text(title, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp, color = extras.muted)
            }
            Spacer(Modifier.height(20.dp))
            Text(rate.percentPerHour?.let { "${Format.oneDecimal(it)} %/h" } ?: "—", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = extras.numberFont)
            Text(rate.mahPerHour?.let { "${it.roundToInt()} mAh/h" } ?: "Needs 5+ min", color = extras.muted, style = MaterialTheme.typography.bodySmall)
            Text(Format.durationMs(rate.durationMs), color = extras.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PastRow(s: SessionEntity) {
    val extras = LocalExtras.current
    val overall = s.overallRate()
    Surface(shape = RoundedCornerShape(20.dp), color = extras.tile, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.startTime)))
                Text(
                    "${Format.durationMs(s.durationMs)} · screen on ${Format.durationMs(s.screenOnMs)}",
                    style = MaterialTheme.typography.bodySmall, color = extras.muted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("−${Format.oneDecimal(s.startPercent - s.endPercent)}%", fontWeight = FontWeight.SemiBold, fontFamily = extras.numberFont)
                Text(
                    "on ${s.screenOnRate().percentPerHour?.let { Format.oneDecimal(it) } ?: "—"} · off ${s.screenOffRate().percentPerHour?.let { Format.oneDecimal(it) } ?: "—"} %/h",
                    style = MaterialTheme.typography.bodySmall, color = extras.muted,
                )
                Text("avg ${overall.percentPerHour?.let { Format.oneDecimal(it) } ?: "—"} %/h", style = MaterialTheme.typography.bodySmall, color = extras.muted)
            }
        }
    }
}
