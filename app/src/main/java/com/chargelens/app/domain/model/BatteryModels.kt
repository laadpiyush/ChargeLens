package com.chargelens.app.domain.model

enum class ChargeStatus(val label: String) {
    CHARGING("Charging"),
    DISCHARGING("Discharging"),
    FULL("Full"),
    NOT_CHARGING("Not charging"),
    UNKNOWN("Unknown"),
}

enum class PlugType(val label: String) {
    AC("AC"),
    USB("USB"),
    WIRELESS("Wireless"),
    DOCK("Dock"),
    NONE("None"),
}

/** Condition reported by the battery HAL (EXTRA_HEALTH). */
enum class BatteryCondition(val label: String) {
    GOOD("Good"),
    OVERHEAT("Overheat"),
    DEAD("Dead"),
    OVER_VOLTAGE("Over voltage"),
    COLD("Cold"),
    FAILURE("Failure"),
    UNKNOWN("Unknown"),
}

enum class ChargeSpeed(val label: String) {
    SLOW("SLOW CHARGING"),
    NORMAL("CHARGING"),
    FAST("FAST CHARGING"),
    SUPER_FAST("SUPER FAST CHARGING"),
    DISCHARGING("DISCHARGING"),
    NOT_CHARGING("NOT CHARGING"),
    FULL("FULL"),
}

enum class CurrentUnit(val label: String) {
    AUTO("Auto-detect"),
    MICRO_AMPS("Microamps (µA)"),
    MILLI_AMPS("Milliamps (mA)"),
}

enum class CurrentSignMode(val label: String) {
    /** Positive while charging, negative while discharging, decided from battery status. */
    AUTO("Auto (from status)"),
    AS_REPORTED("As reported"),
    INVERTED("Inverted"),
}

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

data class SpeedThresholds(
    val slowBelowW: Float = 5f,
    val fastFromW: Float = 10f,
    val superFastFromW: Float = 25f,
)

/** One reading straight from the platform, mapped to domain types but otherwise unprocessed. */
data class RawBatteryReading(
    val level: Int,
    val scale: Int,
    /** EXTRA_VOLTAGE as reported (normally mV, a few devices use V or µV). */
    val voltage: Int?,
    /** EXTRA_TEMPERATURE in tenths of a degree Celsius. */
    val temperatureTenths: Int?,
    val plugType: PlugType,
    val status: ChargeStatus,
    val condition: BatteryCondition,
    /** BATTERY_PROPERTY_CURRENT_NOW as reported (unit and sign vary by OEM). */
    val currentNow: Int?,
    val chargeCounterUah: Int?,
    val capacityPercent: Int?,
    val technology: String? = null,
)

data class BatterySnapshot(
    val timestamp: Long,
    val level: Int,
    /** Battery percentage with two meaningful decimals. */
    val percent: Float,
    val voltageMv: Int?,
    /** Signed: positive while charging, negative while discharging. */
    val currentMa: Int?,
    val powerMw: Int?,
    val temperatureC: Float?,
    val plugType: PlugType,
    val status: ChargeStatus,
    val condition: BatteryCondition,
    val healthPercent: Float?,
    val maxCapacityMah: Int?,
    val designCapacityMah: Int?,
    val remainingMah: Int?,
    val speed: ChargeSpeed,
    val timeToFullMinutes: Int?,
    val timeRemainingMinutes: Int?,
    val technology: String? = null,
) {
    val isPlugged: Boolean get() = plugType != PlugType.NONE
    val powerW: Float? get() = powerMw?.div(1000f)
}

enum class Metric(val label: String, val graphable: Boolean) {
    VOLTAGE("VOLTAGE", true),
    CURRENT("CURRENT", true),
    WATTAGE("WATTAGE", true),
    TEMPERATURE("TEMPERATURE", true),
    HEALTH("HEALTH", false),
    PLUGGED("PLUGGED", false),
    MAX_CAPACITY("MAX CAPACITY", false),
    CHARGE_STATUS("CHARGE STATUS", false),
}

data class MetricStats(val min: Float, val max: Float, val avg: Float, val count: Int)

/** Everything the live screens need: latest snapshot plus rolling series and session stats. */
data class LiveBatteryState(
    val snapshot: BatterySnapshot?,
    /** Rolling samples per graphable metric, oldest first, in display units (mV, mA, W, °C). */
    val series: Map<Metric, List<Float>>,
    /** Min/max/avg since the plug state last changed. */
    val stats: Map<Metric, MetricStats>,
    val sessionStartedAt: Long,
) {
    companion object {
        val Empty = LiveBatteryState(null, emptyMap(), emptyMap(), 0L)
    }
}
