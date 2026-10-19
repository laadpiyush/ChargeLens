package com.chargelens.app.domain

import com.chargelens.app.domain.model.BatterySnapshot
import com.chargelens.app.domain.model.ChargeStatus
import com.chargelens.app.domain.model.CurrentSignMode
import com.chargelens.app.domain.model.CurrentUnit
import com.chargelens.app.domain.model.LiveBatteryState
import com.chargelens.app.domain.model.Metric
import com.chargelens.app.domain.model.PlugType
import com.chargelens.app.domain.model.RawBatteryReading
import com.chargelens.app.domain.model.SpeedThresholds
import kotlin.math.roundToInt

data class ProcessingConfig(
    val currentUnit: CurrentUnit = CurrentUnit.AUTO,
    val signMode: CurrentSignMode = CurrentSignMode.AUTO,
    val thresholds: SpeedThresholds = SpeedThresholds(),
    val designCapacityMah: Int? = null,
)

/**
 * Turns raw readings into [LiveBatteryState]. Stateful: it learns the device's current unit and
 * sign convention, smooths the capacity estimate and current, and keeps rolling series and
 * per-session stats. Not thread-safe; drive it from a single coroutine.
 */
class SnapshotProcessor(
    initialMaxCapacityMah: Float? = null,
    private val historySize: Int = HISTORY_SIZE,
) {
    @Volatile
    var maxCapacityEstimate: Float? = initialMaxCapacityMah
        private set

    fun resetCapacityEstimate() {
        maxCapacityEstimate = null
    }

    private var smoothedCurrent: Float? = null
    private var lockedMicroAmps = false
    private var deviceInverted: Boolean? = null
    private var lastPlug: PlugType? = null
    private var sessionStart = 0L
    private var lastCapacityPercent: Int? = null
    private var lastShownPercent: Float? = null

    private val graphable = Metric.entries.filter { it.graphable }
    private val series = graphable.associateWith { RollingSeries(historySize) }
    private val stats = graphable.associateWith { StatsAccumulator() }

    fun process(raw: RawBatteryReading, config: ProcessingConfig, now: Long): LiveBatteryState {
        if (lastPlug != raw.plugType) {
            if (lastPlug != null) {
                stats.values.forEach { it.reset() }
                smoothedCurrent = null
                lastShownPercent = null
            }
            lastPlug = raw.plugType
            sessionStart = now
        }

        val voltageMv = BatteryMath.normalizeVoltageMv(raw.voltage)
        val currentMa = raw.currentNow?.let { normalizeCurrent(it, raw.status, config) }

        // Learn capacity only at the moment the integer percentage ticks over. Sampling every
        // second makes the estimate sawtooth between counter/L and counter/(L+1), which in turn
        // makes the decimal percentage wobble up and down.
        val levelTicked = lastCapacityPercent != null && raw.capacityPercent != lastCapacityPercent
        if (levelTicked || maxCapacityEstimate == null) {
            BatteryMath.capacitySampleMah(raw.chargeCounterUah, raw.capacityPercent)?.let { sample ->
                // A wildly different sample (e.g. first reading after a bad estimate) resets the EMA.
                val prev = maxCapacityEstimate
                maxCapacityEstimate = if (prev == null || sample !in prev * 0.5f..prev * 1.5f) sample
                else BatteryMath.ema(prev, sample, CAPACITY_ALPHA)
            }
        }
        raw.capacityPercent?.let { lastCapacityPercent = it }
        val maxCap = maxCapacityEstimate

        val rawPercent = BatteryMath.precisePercent(raw.chargeCounterUah, maxCap, raw.level, raw.scale)
        val percent = BatteryMath.monotonicPercent(lastShownPercent, rawPercent, currentMa)
        lastShownPercent = percent
        val remainingMah = raw.chargeCounterUah?.takeIf { it > 0 }?.div(1000f)
            ?: maxCap?.times(percent / 100f)

        val powerMw = if (voltageMv != null && currentMa != null) BatteryMath.powerMw(voltageMv, currentMa) else null
        val tempC = raw.temperatureTenths?.takeIf { it > -500 }?.div(10f)

        if (currentMa != null) smoothedCurrent = BatteryMath.ema(smoothedCurrent, currentMa, CURRENT_ALPHA)

        val design = config.designCapacityMah
        val snapshot = BatterySnapshot(
            timestamp = now,
            level = if (raw.scale > 0) raw.level * 100 / raw.scale else raw.level,
            percent = percent,
            voltageMv = voltageMv,
            currentMa = currentMa?.roundToInt(),
            powerMw = powerMw?.roundToInt(),
            temperatureC = tempC,
            plugType = raw.plugType,
            status = raw.status,
            condition = raw.condition,
            healthPercent = BatteryMath.healthPercent(maxCap, design),
            maxCapacityMah = maxCap?.roundToInt(),
            designCapacityMah = design,
            remainingMah = remainingMah?.roundToInt(),
            speed = BatteryMath.chargeSpeed(currentMa, powerMw?.div(1000f), raw.status, raw.plugType != PlugType.NONE, config.thresholds),
            timeToFullMinutes = if (raw.status == ChargeStatus.FULL) 0
            else BatteryMath.timeToFullMinutes(remainingMah, maxCap, smoothedCurrent),
            timeRemainingMinutes = BatteryMath.timeRemainingMinutes(remainingMah, smoothedCurrent),
            technology = raw.technology,
        )

        record(Metric.VOLTAGE, voltageMv?.toFloat())
        record(Metric.CURRENT, currentMa)
        record(Metric.WATTAGE, powerMw?.div(1000f))
        record(Metric.TEMPERATURE, tempC)

        return LiveBatteryState(
            snapshot = snapshot,
            series = series.mapValues { it.value.toList() },
            stats = stats.mapNotNull { (k, v) -> v.snapshot()?.let { k to it } }.toMap(),
            sessionStartedAt = sessionStart,
        )
    }

    private fun record(metric: Metric, value: Float?) {
        if (value == null) return
        series.getValue(metric).add(value)
        stats.getValue(metric).add(value)
    }

    private fun normalizeCurrent(raw: Int, status: ChargeStatus, config: ProcessingConfig): Float {
        val micro = when (config.currentUnit) {
            CurrentUnit.MICRO_AMPS -> true
            CurrentUnit.MILLI_AMPS -> false
            CurrentUnit.AUTO -> {
                // Once a µA-sized value has been seen, stay in µA so small currents near full
                // charge are not misread as amps.
                if (BatteryMath.looksLikeMicroAmps(raw)) lockedMicroAmps = true
                lockedMicroAmps
            }
        }
        val ma = BatteryMath.toMilliAmps(raw, micro)
        // Learn the device's sign convention from unambiguous states.
        if (ma > 50f || ma < -50f) {
            when (status) {
                ChargeStatus.CHARGING -> deviceInverted = ma < 0f
                ChargeStatus.DISCHARGING -> deviceInverted = ma > 0f
                else -> Unit
            }
        }
        return BatteryMath.applySign(ma, status, config.signMode, deviceInverted)
    }

    companion object {
        const val HISTORY_SIZE = 300
        const val SPARKLINE_SIZE = 60
        private const val CAPACITY_ALPHA = 0.1f
        private const val CURRENT_ALPHA = 0.1f
    }
}
