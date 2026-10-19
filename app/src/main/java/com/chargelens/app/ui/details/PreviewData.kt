package com.chargelens.app.ui.details

import com.chargelens.app.domain.model.BatteryCondition
import com.chargelens.app.domain.model.BatterySnapshot
import com.chargelens.app.domain.model.ChargeSpeed
import com.chargelens.app.domain.model.ChargeStatus
import com.chargelens.app.domain.model.LiveBatteryState
import com.chargelens.app.domain.model.Metric
import com.chargelens.app.domain.model.MetricStats
import com.chargelens.app.domain.model.PlugType
import com.chargelens.app.ui.components.MonitoringState
import kotlin.math.sin

/** Fake data for previews and UI tests. */
object PreviewData {
    private fun wave(base: Float, amp: Float, n: Int = 60) = List(n) { base + amp * sin(it / 6f) + amp * 0.3f * sin(it / 1.7f) }

    val chargingSnapshot = BatterySnapshot(
        timestamp = 0L,
        level = 45,
        percent = 45.48f,
        voltageMv = 3994,
        currentMa = 3073,
        powerMw = 12274,
        temperatureC = 37.6f,
        plugType = PlugType.AC,
        status = ChargeStatus.CHARGING,
        condition = BatteryCondition.GOOD,
        healthPercent = 100f,
        maxCapacityMah = 4903,
        designCapacityMah = 4855,
        remainingMah = 2230,
        speed = ChargeSpeed.FAST,
        timeToFullMinutes = 52,
        timeRemainingMinutes = null,
        technology = "Li-ion",
    )

    val charging = DetailsUiState(
        battery = LiveBatteryState(
            snapshot = chargingSnapshot,
            series = mapOf(
                Metric.VOLTAGE to wave(3990f, 8f),
                Metric.CURRENT to wave(3050f, 120f),
                Metric.WATTAGE to wave(12.2f, 0.5f),
                Metric.TEMPERATURE to wave(37.4f, 0.3f),
            ),
            stats = mapOf(
                Metric.VOLTAGE to MetricStats(3850f, 4002f, 3960f, 600),
                Metric.CURRENT to MetricStats(2780f, 3210f, 3040f, 600),
                Metric.WATTAGE to MetricStats(10.9f, 12.8f, 12.0f, 600),
                Metric.TEMPERATURE to MetricStats(33.1f, 37.9f, 36.2f, 600),
            ),
            sessionStartedAt = 0L,
        ),
        monitoring = MonitoringState.ACTIVE,
    )

    val discharging = charging.copy(
        battery = charging.battery.copy(
            snapshot = chargingSnapshot.copy(
                currentMa = -412,
                powerMw = -1590,
                plugType = PlugType.NONE,
                status = ChargeStatus.DISCHARGING,
                speed = ChargeSpeed.DISCHARGING,
                temperatureC = 29.4f,
                timeToFullMinutes = null,
                timeRemainingMinutes = 318,
            ),
            series = charging.battery.series + (Metric.CURRENT to wave(-420f, 60f)) + (Metric.WATTAGE to wave(-1.6f, 0.2f)),
        ),
        monitoring = MonitoringState.OFF,
    )
}
