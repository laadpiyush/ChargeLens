package com.chargelens.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chargelens.app.ui.theme.LocalExtras

enum class MonitoringState {
    OFF,
    ACTIVE,
    /** On battery, dormant listener running: plugging in starts monitoring by itself. */
    WAITING_IN_BACKGROUND,
    /** On battery, nothing running: monitoring starts when the app is opened on the charger. */
    STARTS_WHEN_OPENED,
}

@Composable
fun MonitoringCard(
    state: MonitoringState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state != MonitoringState.OFF
    val extras = LocalExtras.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = extras.card,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        when (state) {
                            MonitoringState.OFF -> extras.inactive
                            MonitoringState.ACTIVE -> extras.accent
                            else -> extras.accent.copy(alpha = 0.45f)
                        },
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(14.dp))
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { stateDescription = if (active) "On" else "Off" },
            ) {
                Text(
                    when (state) {
                        MonitoringState.OFF -> "Monitoring Paused"
                        MonitoringState.ACTIVE -> "Monitoring Active"
                        MonitoringState.WAITING_IN_BACKGROUND -> "Waiting for Charger"
                        MonitoringState.STARTS_WHEN_OPENED -> "Ready for Charging"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    when (state) {
                        MonitoringState.WAITING_IN_BACKGROUND -> "Monitoring starts automatically when you plug in"
                        MonitoringState.STARTS_WHEN_OPENED -> "Nothing runs on battery. Monitoring starts when you open ChargeLens on the charger"
                        else -> "Powers charging stats, battery health, and Always On Display"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.muted,
                )
            }
            TextButton(onClick = onToggle) {
                Text(if (active) "Turn off" else "Turn on", color = extras.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
