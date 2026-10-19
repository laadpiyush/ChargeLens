package com.chargelens.app.domain

import com.chargelens.app.domain.model.ChargeSpeed
import com.chargelens.app.domain.model.ChargeStatus
import com.chargelens.app.domain.model.CurrentSignMode
import com.chargelens.app.domain.model.SpeedThresholds
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure battery maths, kept free of Android types so it is unit-testable on the JVM. */
object BatteryMath {

    /** Raw CURRENT_NOW magnitudes at or above this are treated as µA (no phone draws 15 A). */
    const val MICRO_AMP_THRESHOLD = 15_000

    /** Normalises EXTRA_VOLTAGE to mV. Some devices report volts, a few report µV. */
    fun normalizeVoltageMv(raw: Int?): Int? = when {
        raw == null || raw <= 0 -> null
        raw < 100 -> raw * 1000
        raw > 100_000 -> raw / 1000
        else -> raw
    }

    fun looksLikeMicroAmps(raw: Int): Boolean = abs(raw) >= MICRO_AMP_THRESHOLD

    fun toMilliAmps(raw: Int, isMicroAmps: Boolean): Float =
        if (isMicroAmps) raw / 1000f else raw.toFloat()

    /**
     * Applies a sign convention so the result is positive while charging and negative while
     * discharging.
     *
     * @param deviceInverted learned convention for this device (true if it reports charging as
     * negative); used when status alone cannot decide (FULL / UNKNOWN).
     */
    fun applySign(
        milliAmps: Float,
        status: ChargeStatus,
        mode: CurrentSignMode,
        deviceInverted: Boolean?,
    ): Float = when (mode) {
        CurrentSignMode.AS_REPORTED -> milliAmps
        CurrentSignMode.INVERTED -> -milliAmps
        CurrentSignMode.AUTO -> when (status) {
            ChargeStatus.CHARGING -> abs(milliAmps)
            ChargeStatus.DISCHARGING, ChargeStatus.NOT_CHARGING -> -abs(milliAmps)
            ChargeStatus.FULL, ChargeStatus.UNKNOWN ->
                if (deviceInverted == true) -milliAmps else milliAmps
        }
    }

    /** P[mW] = V[mV] × I[mA] / 1000 */
    fun powerMw(voltageMv: Int, currentMa: Float): Float = voltageMv * currentMa / 1000f

    /** One capacity sample in mAh from charge counter (µAh) and the integer capacity %. */
    fun capacitySampleMah(chargeCounterUah: Int?, capacityPercent: Int?): Float? {
        if (chargeCounterUah == null || chargeCounterUah <= 0) return null
        // Low percentages amplify the 1 % quantisation error, so ignore them.
        if (capacityPercent == null || capacityPercent < 15 || capacityPercent > 100) return null
        return (chargeCounterUah / 1000f) / (capacityPercent / 100f)
    }

    fun ema(previous: Float?, sample: Float, alpha: Float): Float =
        if (previous == null) sample else previous + alpha * (sample - previous)

    /**
     * Precise percentage = remaining charge / estimated full capacity, falling back to level/scale.
     * Results that disagree with the integer level by more than 2 points are rejected: some
     * kernels report charge counter in odd units and the integer level is the safer value.
     */
    fun precisePercent(
        chargeCounterUah: Int?,
        maxCapacityMah: Float?,
        level: Int,
        scale: Int,
    ): Float {
        val fallback = if (scale > 0) level * 100f / scale else level.toFloat()
        if (chargeCounterUah == null || chargeCounterUah <= 0 || maxCapacityMah == null || maxCapacityMah <= 0f) {
            return fallback.coerceIn(0f, 100f)
        }
        val precise = (chargeCounterUah / 1000f) / maxCapacityMah * 100f
        return if (abs(precise - fallback) > 2f) fallback.coerceIn(0f, 100f) else precise.coerceIn(0f, 100f)
    }

    /**
     * Keeps the shown percentage moving in one direction: never down while charging, never up
     * while discharging. Fuel gauges update the charge counter in steps (every ~10 s on Samsung)
     * and small corrections would otherwise show as the number going backwards. A jump of more
     * than 1.5 points (e.g. the OS recalibrating) is accepted as-is.
     */
    fun monotonicPercent(previous: Float?, raw: Float, currentMa: Float?): Float {
        if (previous == null || abs(raw - previous) > 1.5f) return raw
        return when {
            currentMa != null && currentMa > 20f -> maxOf(previous, raw)
            currentMa != null && currentMa < -20f -> minOf(previous, raw)
            else -> raw
        }
    }

    /** Estimated health = full-charge capacity / design capacity, capped at 100 %. */
    fun healthPercent(maxCapacityMah: Float?, designCapacityMah: Int?): Float? {
        if (maxCapacityMah == null || designCapacityMah == null || designCapacityMah <= 0) return null
        return (maxCapacityMah / designCapacityMah * 100f).coerceIn(0f, 100f)
    }

    fun chargeSpeed(
        currentMa: Float?,
        powerW: Float?,
        status: ChargeStatus,
        plugged: Boolean,
        thresholds: SpeedThresholds,
    ): ChargeSpeed {
        if (status == ChargeStatus.FULL) return ChargeSpeed.FULL
        if (currentMa != null && currentMa < 0f) return ChargeSpeed.DISCHARGING
        if (!plugged && status != ChargeStatus.CHARGING) return ChargeSpeed.DISCHARGING
        if (status == ChargeStatus.NOT_CHARGING) return ChargeSpeed.NOT_CHARGING
        val w = powerW ?: return ChargeSpeed.NORMAL
        return when {
            w < thresholds.slowBelowW -> ChargeSpeed.SLOW
            w < thresholds.fastFromW -> ChargeSpeed.NORMAL
            w < thresholds.superFastFromW -> ChargeSpeed.FAST
            else -> ChargeSpeed.SUPER_FAST
        }
    }

    /** Minutes until full at the given (smoothed) charging current, or null if not charging. */
    fun timeToFullMinutes(remainingMah: Float?, maxCapacityMah: Float?, currentMa: Float?): Int? {
        if (remainingMah == null || maxCapacityMah == null || currentMa == null || currentMa < 20f) return null
        val missing = (maxCapacityMah - remainingMah).coerceAtLeast(0f)
        return (missing / currentMa * 60f).roundToInt()
    }

    /** Minutes until empty at the given (smoothed) discharge current, or null if charging. */
    fun timeRemainingMinutes(remainingMah: Float?, currentMa: Float?): Int? {
        if (remainingMah == null || currentMa == null || currentMa > -5f) return null
        return (remainingMah / -currentMa * 60f).roundToInt()
    }

    fun celsiusToFahrenheit(c: Float): Float = c * 9f / 5f + 32f
}
