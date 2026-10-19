package com.chargelens.app.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chargelens.app.domain.model.Metric
import com.chargelens.app.ui.components.LabeledRow
import com.chargelens.app.ui.components.MetricTile
import com.chargelens.app.ui.components.SubPageTitle
import com.chargelens.app.ui.components.SectionCard
import com.chargelens.app.ui.components.SwitchRow
import com.chargelens.app.ui.details.GaugeHero
import com.chargelens.app.ui.details.buildTileModel
import com.chargelens.app.ui.theme.LocalExtras
import com.chargelens.app.ui.theme.Palette
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignScreen(onBack: () -> Unit, viewModel: DesignViewModel = hiltViewModel()) {
    val state by viewModel.preview.collectAsStateWithLifecycle()
    val design = state.design

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SubPageTitle("Design", onBack) }

        // Live preview (the app theme already reflects the current design settings).
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    GaugeHero(state, Modifier.fillMaxWidth())
                }
                val tile = design.visibleTiles.firstOrNull() ?: Metric.WATTAGE
                val model = buildTileModel(tile, state, LocalExtras.current)
                MetricTile(
                    icon = model.icon, label = tile.label, value = model.value,
                    accessibilityLabel = model.accessibilityLabel, iconTint = model.iconTint,
                    valueColor = model.valueColor ?: MaterialTheme.colorScheme.onSurface,
                    sparkline = model.sparkline, sparkColor = model.sparkColor,
                    modifier = Modifier.weight(0.9f),
                )
            }
        }

        item {
            SectionCard("Accent colour") {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(Palette.Accents) { argb ->
                        val selected = argb == design.accentArgb
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(Color(argb), CircleShape)
                                .then(if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
                                .selectable(selected = selected, role = Role.RadioButton) { viewModel.setAccent(argb) }
                                .semantics { contentDescription = "Accent colour ${Palette.Accents.indexOf(argb) + 1}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Rounded.Check, null, tint = Color(0xFF10210F))
                        }
                    }
                }
            }
        }

        item {
            SectionCard("Gauge & type") {
                LabeledRow("Gauge thickness", "${design.gaugeThicknessDp.roundToInt()} dp")
                Slider(
                    value = design.gaugeThicknessDp,
                    onValueChange = viewModel::setThickness,
                    valueRange = 8f..32f,
                    steps = 11,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                LabeledRow("Number font")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
                    SegmentedButton(selected = !design.monoFont, onClick = { viewModel.setMono(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("System") }
                    SegmentedButton(selected = design.monoFont, onClick = { viewModel.setMono(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Mono") }
                }
                Spacer(Modifier.height(4.dp))
                SwitchRow("AMOLED black", design.amoledBlack, viewModel::setAmoled, "Pure black background everywhere")
            }
        }

        item {
            SectionCard("Tiles") {
                design.tileOrder.forEachIndexed { index, metric ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = metric !in design.hiddenTiles,
                            onCheckedChange = { viewModel.setTileVisible(metric, it) },
                        )
                        Text(
                            metric.label.lowercase().replaceFirstChar(Char::uppercase),
                            modifier = Modifier.weight(1f),
                            color = if (metric in design.hiddenTiles) LocalExtras.current.muted else MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = { viewModel.moveTile(metric, -1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, "Move ${metric.label.lowercase()} up")
                        }
                        IconButton(onClick = { viewModel.moveTile(metric, 1) }, enabled = index < design.tileOrder.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Move ${metric.label.lowercase()} down")
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                OutlinedButton(onClick = viewModel::reset) { Text("Reset to defaults") }
                Spacer(Modifier.width(1.dp))
            }
        }
    }
}
