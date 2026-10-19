package com.chargelens.app.ui.details

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import com.chargelens.app.domain.SnapshotProcessor
import com.chargelens.app.domain.model.BatterySnapshot
import com.chargelens.app.domain.model.Metric
import com.chargelens.app.domain.model.TemperatureUnit
import com.chargelens.app.ui.theme.ChargeLensExtras
import com.chargelens.app.util.Format
import kotlin.math.roundToInt

data class TileModel(
    val metric: Metric,
    val icon: ImageVector,
    val value: String,
    val accessibilityLabel: String,
    val iconTint: Color,
    val valueColor: Color?,
    val sparkline: List<Float>?,
    val sparkColor: Color,
)

const val NOT_AVAILABLE = "N/A"

fun Metric.icon(): ImageVector = when (this) {
    Metric.VOLTAGE -> Icons.Outlined.ElectricBolt
    Metric.CURRENT -> Icons.Outlined.Waves
    Metric.WATTAGE -> Icons.Outlined.Bolt
    Metric.TEMPERATURE -> Icons.Outlined.Thermostat
    Metric.HEALTH -> Icons.Outlined.HealthAndSafety
    Metric.PLUGGED -> Icons.Outlined.Power
    Metric.MAX_CAPACITY -> Icons.Outlined.BatteryFull
    Metric.CHARGE_STATUS -> Icons.Outlined.BatteryChargingFull
}

/** Display string for a metric's current value ("N/A" when the device does not report it). */
fun Metric.valueText(s: BatterySnapshot?, unit: TemperatureUnit): String {
    if (s == null) return "—"
    return when (this) {
        Metric.VOLTAGE -> s.voltageMv?.let { "$it mV" }
        Metric.CURRENT -> s.currentMa?.let { "${Format.signed(it)} mA" }
        Metric.WATTAGE -> s.powerMw?.let { Format.watts(it) }
        Metric.TEMPERATURE -> s.temperatureC?.let { Format.temperature(it, unit) }
        Metric.HEALTH -> s.healthPercent?.let { "${it.roundToInt()}%" }
        Metric.PLUGGED -> s.plugType.label
        Metric.MAX_CAPACITY -> s.maxCapacityMah?.let { "$it mAh" }
        Metric.CHARGE_STATUS -> s.status.label
    } ?: NOT_AVAILABLE
}

/** Spoken form for TalkBack (units written out, sign spoken). */
fun Metric.spokenValue(s: BatterySnapshot?, unit: TemperatureUnit): String {
    if (s == null) return "loading"
    fun signedWord(v: Number) = if (v.toFloat() < 0) "minus ${-v.toFloat()}" else v.toString()
    return when (this) {
        Metric.VOLTAGE -> s.voltageMv?.let { "$it millivolts" }
        Metric.CURRENT -> s.currentMa?.let { "${signedWord(it)} milliamps, ${if (it >= 0) "charging" else "discharging"}" }
        Metric.WATTAGE -> s.powerMw?.let { "${signedWord(Format.oneDecimal(it / 1000f).toFloat())} watts" }
        Metric.TEMPERATURE -> s.temperatureC?.let {
            "${Format.oneDecimal(Format.temperatureValue(it, unit))} degrees ${if (unit == TemperatureUnit.FAHRENHEIT) "Fahrenheit" else "Celsius"}"
        }
        Metric.HEALTH -> s.healthPercent?.let { "${it.roundToInt()} percent" }
        Metric.PLUGGED -> s.plugType.label
        Metric.MAX_CAPACITY -> s.maxCapacityMah?.let { "$it milliamp hours" }
        Metric.CHARGE_STATUS -> s.status.label
    } ?: "not available"
}

/** Series converted to display units (temperature honours °C/°F). */
fun Metric.displaySeries(series: Map<Metric, List<Float>>, unit: TemperatureUnit): List<Float> {
    val raw = series[this].orEmpty()
    return if (this == Metric.TEMPERATURE && unit == TemperatureUnit.FAHRENHEIT) raw.map { Format.temperatureValue(it, unit) } else raw
}

fun buildTileModel(
    metric: Metric,
    state: DetailsUiState,
    extras: ChargeLensExtras,
): TileModel {
    val s = state.battery.snapshot
    val unit = state.temperatureUnit
    val discharging = (s?.currentMa ?: 0) < 0
    val signColor = if (discharging) extras.discharge else extras.accent
    val tempTint = s?.temperatureC?.let { lerp(extras.accent, extras.hot, ((it - 30f) / 15f).coerceIn(0f, 1f)) } ?: extras.accent

    val (iconTint, valueColor, sparkColor) = when (metric) {
        Metric.CURRENT, Metric.WATTAGE -> Triple(signColor, if (s?.currentMa != null) signColor else null, signColor)
        Metric.TEMPERATURE -> Triple(tempTint, null, tempTint)
        else -> Triple(extras.accent, null, extras.accent)
    }
    return TileModel(
        metric = metric,
        icon = metric.icon(),
        value = metric.valueText(s, unit),
        accessibilityLabel = "${metric.label.lowercase().replaceFirstChar(Char::uppercase)}: ${metric.spokenValue(s, unit)}",
        iconTint = iconTint,
        valueColor = valueColor,
        sparkline = if (metric.graphable) metric.displaySeries(state.battery.series, unit).takeLast(SnapshotProcessor.SPARKLINE_SIZE) else null,
        sparkColor = sparkColor,
    )
}
