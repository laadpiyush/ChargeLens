package com.chargelens.app.ui.details

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chargelens.app.domain.model.BatterySnapshot
import com.chargelens.app.domain.model.ChargeSpeed
import com.chargelens.app.domain.model.Metric
import com.chargelens.app.ui.components.BatteryGauge
import com.chargelens.app.ui.components.MetricTile
import com.chargelens.app.ui.components.MonitoringCard
import com.chargelens.app.ui.theme.ChargeLensTheme
import com.chargelens.app.ui.theme.LocalExtras
import com.chargelens.app.util.Format

/**
 * The Details screen. Stateless apart from local UI state, so the same composable is hosted by the
 * main app, [com.chargelens.app.ui.charging.ChargingActivity], the screen saver and the overlay.
 */
@Composable
fun DetailsScreen(
    state: DetailsUiState,
    onToggleMonitoring: () -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = { DetailsTopBar() },
    enableDetailSheets: Boolean = true,
) {
    var selected by rememberSaveable { mutableStateOf<Metric?>(null) }
    val onTileClick: ((Metric) -> Unit)? = if (enableDetailSheets) { m -> selected = m } else null

    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight && maxWidth >= 560.dp
        Column(Modifier.fillMaxSize()) {
            topBar()
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(0.42f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        GaugeHero(state, Modifier.fillMaxHeight(0.92f))
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(0.58f).testTag("details_grid"),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        monitoringItem(state, onToggleMonitoring)
                        tiles(state, onTileClick)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().testTag("details_grid"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "gauge") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            GaugeHero(state, Modifier.fillMaxWidth(0.8f).widthIn(max = 360.dp))
                        }
                    }
                    monitoringItem(state, onToggleMonitoring)
                    tiles(state, onTileClick)
                }
            }
        }
    }

    selected?.let { metric ->
        MetricDetailSheet(metric = metric, state = state, onDismiss = { selected = null })
    }
}

private fun LazyGridScope.monitoringItem(state: DetailsUiState, onToggle: () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "monitoring") {
        MonitoringCard(state = state.monitoring, onToggle = onToggle, modifier = Modifier.padding(bottom = 4.dp))
    }
}

private fun LazyGridScope.tiles(state: DetailsUiState, onClick: ((Metric) -> Unit)?) {
    items(state.design.visibleTiles, key = { it.name }) { metric ->
        val model = buildTileModel(metric, state, LocalExtras.current)
        MetricTile(
            icon = model.icon,
            label = metric.label,
            value = model.value,
            accessibilityLabel = model.accessibilityLabel,
            iconTint = model.iconTint,
            valueColor = model.valueColor ?: MaterialTheme.colorScheme.onSurface,
            sparkline = model.sparkline,
            sparkColor = model.sparkColor,
            onClick = onClick?.let { { it(metric) } },
            modifier = Modifier.testTag("tile_${metric.name}"),
        )
    }
}

@Composable
fun GaugeHero(
    state: DetailsUiState,
    modifier: Modifier = Modifier,
    /** Non-null pins the time estimate on or off; null lets the user toggle it by tapping. */
    showEstimate: Boolean? = null,
) {
    val extras = LocalExtras.current
    val s = state.battery.snapshot
    var estimateToggled by rememberSaveable { mutableStateOf(false) }
    val estimateVisible = showEstimate ?: estimateToggled
    val percent by animateFloatAsState(s?.percent ?: 0f, tween(900), label = "percent")
    val discharging = s?.speed == ChargeSpeed.DISCHARGING

    // Text scales with the gauge so small previews (Design tab) and landscape don't overflow.
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
    val scale = (minOf(maxWidth, maxHeight) / 300.dp).coerceIn(0.45f, 1.2f)
    BatteryGauge(
        progress = percent / 100f,
        thickness = extras.gaugeThickness,
        progressColor = extras.accent,
        trackColor = extras.track,
        modifier = Modifier
            .then(
                // Only tappable when the estimate is user-toggled; otherwise taps pass through
                // (the always-on view uses a tap anywhere to dismiss).
                if (showEstimate == null) {
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { estimateToggled = !estimateToggled }
                } else Modifier,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("Battery ${s?.let { Format.percent(it.percent) } ?: "unknown"} percent")
                    s?.let { append(", ${it.speed.label.lowercase()}") }
                    s?.powerMw?.let { append(", ${Format.watts(it)}") }
                    estimateText(s)?.let { append(", $it") }
                }
            }
            .testTag("gauge"),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (s == null) "--.--" else Format.percent(percent),
                fontFamily = extras.numberFont,
                fontSize = 60.sp * scale,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                modifier = Modifier.alignByBaseline().testTag("percent"),
            )
            Text("%", fontSize = 22.sp * scale, fontWeight = FontWeight.Medium, color = extras.muted, modifier = Modifier.alignByBaseline().padding(start = 2.dp))
        }
        Text(
            s?.speed?.label ?: "READING…",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 3.sp,
            color = extras.muted,
            modifier = Modifier.testTag("status_label"),
        )
        Spacer(Modifier.height(14.dp))
        val pillColor = if (discharging) extras.discharge else extras.accent
        Surface(shape = CircleShape, color = pillColor.copy(alpha = 0.08f), border = BorderStroke(1.dp, pillColor.copy(alpha = 0.7f))) {
            Text(
                s?.powerMw?.let { Format.milliWatts(it) } ?: "— mW",
                fontFamily = extras.numberFont,
                style = MaterialTheme.typography.labelLarge,
                color = pillColor,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        AnimatedVisibility(visible = estimateVisible) {
            Text(
                estimateText(s) ?: "Estimating…",
                style = MaterialTheme.typography.bodySmall,
                color = extras.muted,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
    }
}

private fun estimateText(s: BatterySnapshot?): String? = when {
    s == null -> null
    s.speed == ChargeSpeed.FULL || (s.isPlugged && s.timeToFullMinutes == 0) -> "Fully charged"
    s.isPlugged -> s.timeToFullMinutes?.let { "Full in ${Format.duration(it)}" }
    else -> s.timeRemainingMinutes?.let { "${Format.duration(it)} remaining" }
}

@Composable
fun DetailsTopBar(
    menuItems: List<Pair<String, () -> Unit>> = emptyList(),
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "ChargeLens",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                menuItems.forEach { (label, action) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; action() })
                }
            }
        }
    }
}

@Composable
fun ChargingTopBar(onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("ChargeLens", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose, modifier = Modifier.testTag("close")) {
            Icon(Icons.Rounded.Close, contentDescription = "Close charging screen")
        }
    }
}

@Preview(name = "Charging", showBackground = true, backgroundColor = 0xFF0F1512, widthDp = 400, heightDp = 900)
@Composable
private fun DetailsChargingPreview() {
    ChargeLensTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DetailsScreen(state = PreviewData.charging, onToggleMonitoring = {})
        }
    }
}

@Preview(name = "Discharging", showBackground = true, backgroundColor = 0xFF0F1512, widthDp = 400, heightDp = 900)
@Composable
private fun DetailsDischargingPreview() {
    ChargeLensTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DetailsScreen(state = PreviewData.discharging, onToggleMonitoring = {})
        }
    }
}

@Preview(name = "Landscape", showBackground = true, backgroundColor = 0xFF0F1512, widthDp = 900, heightDp = 420, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetailsLandscapePreview() {
    ChargeLensTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DetailsScreen(state = PreviewData.charging, onToggleMonitoring = {})
        }
    }
}
